# EC2 배포

`main` 브랜치에 반영된 커밋은 GitHub Actions의 `CD` 워크플로를 통해 EC2에 배포됩니다. GitHub와 EC2 사이의 SSH 접속이나 PEM 키 대신 GitHub OIDC와 AWS Systems Manager(SSM)를 사용합니다.

현재 저장소가 공개되어 있으므로 EC2가 GitHub에서 배포 대상 커밋의 소스를 직접 내려받아 JAR를 빌드합니다. 저장소를 비공개로 전환할 때는 S3, ECR 등의 인증된 산출물 전달 방식으로 변경해야 합니다.

## 배포 흐름

1. GitHub Actions가 Java 21 환경에서 `./gradlew clean build`를 실행합니다.
2. GitHub Actions가 OIDC로 배포용 IAM 역할의 임시 권한을 받습니다.
3. SSM Run Command가 EC2에서 배포 대상 커밋의 소스 압축 파일을 내려받습니다.
4. EC2가 `./gradlew bootJar`로 실행 JAR를 빌드합니다.
5. 기존 `/opt/flout/flout-server.jar`를 이전 버전으로 백업하고 `flout` 서비스를 재시작합니다.
6. `http://127.0.0.1:8080/actuator/health`가 `UP`인지 최대 90초간 확인합니다.
7. Health check가 실패하면 이전 JAR로 자동 롤백합니다.
8. 서버의 파일 잠금으로 원격 배포가 겹치지 않게 하며, Actions 대기 시간이 끝나면 실행 중인 SSM 명령의 취소를 요청합니다.

동시 배포는 `flout-main-deployment` concurrency group을 통해 직렬화됩니다. 자동 배포는 `main` push에서만 실행됩니다. GitHub Actions 화면의 수동 실행도 `main` 브랜치를 선택해야 합니다.

## 1. EC2 사전 설정

EC2용 IAM 역할에는 AWS 관리형 정책 `AmazonSSMManagedInstanceCore`를 연결합니다. 사진 저장 기능에 필요한 S3 권한은 배포 권한과 별도로 관리합니다.

Ubuntu에서 SSM Agent 상태를 확인합니다.

```bash
sudo systemctl status snap.amazon-ssm-agent.amazon-ssm-agent.service --no-pager
```

중지 상태라면 시작합니다.

```bash
sudo snap start amazon-ssm-agent
```

AWS 콘솔의 **Systems Manager → Fleet Manager → 관리형 노드**에 인스턴스가 나타나야 합니다. SSM은 AWS API로 아웃바운드 통신하므로 보안 그룹에서 GitHub Actions를 위한 SSH 22번 인바운드를 열 필요가 없습니다.

EC2에는 다음 항목이 필요합니다.

- JDK 21 (EC2에서 `./gradlew bootJar`를 실행하므로 JRE만으로는 부족합니다)
- `curl`, `tar`, `flock`
- 외부 GitHub 및 Gradle 배포 서버로 연결 가능한 아웃바운드 네트워크
- `/opt/flout/flout-server.jar`
- `/etc/flout/flout.env`
- systemd의 `flout.service`

서버의 환경변수, MySQL 데이터 및 NGINX 설정은 CD가 수정하지 않습니다.

## 2. GitHub OIDC 공급자와 배포 역할 생성

AWS IAM의 **자격 증명 공급자**에서 다음 OIDC 공급자를 한 번만 생성합니다.

- 공급자 URL: `https://token.actions.githubusercontent.com`
- 대상: `sts.amazonaws.com`

그다음 GitHub Actions가 맡을 배포 역할을 생성합니다. `AWS_ACCOUNT_ID`, `AWS_REGION`, `INSTANCE_ID`를 실제 값으로 바꿉니다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::AWS_ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
          "token.actions.githubusercontent.com:sub": [
            "repo:CUK-FLOUT/flout-server:ref:refs/heads/main",
            "repo:CUK-FLOUT@343158041/flout-server@1421181234:ref:refs/heads/main"
          ]
        }
      }
    }
  ]
}
```

GitHub Actions 배포 역할에는 다음 인라인 정책을 연결합니다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "SendDeploymentCommand",
      "Effect": "Allow",
      "Action": "ssm:SendCommand",
      "Resource": [
        "arn:aws:ec2:AWS_REGION:AWS_ACCOUNT_ID:instance/INSTANCE_ID",
        "arn:aws:ssm:AWS_REGION::document/AWS-RunShellScript"
      ]
    },
    {
      "Sid": "ControlDeploymentCommand",
      "Effect": "Allow",
      "Action": [
        "ssm:GetCommandInvocation",
        "ssm:CancelCommand"
      ],
      "Resource": "*"
    }
  ]
}
```

## 3. GitHub Actions Variables 등록

Repository의 **Settings → Secrets and variables → Actions → Variables**에 다음 Repository variable을 등록합니다. 이 값들은 비밀번호나 개인 키가 아니므로 Secrets가 아닌 Variables로 관리합니다.

| 이름 | 값 |
| --- | --- |
| `AWS_REGION` | `ap-northeast-2` |
| `AWS_DEPLOY_ROLE_ARN` | GitHub Actions용 배포 역할 ARN |
| `EC2_INSTANCE_ID` | 개발 EC2 인스턴스 ID (`i-...`) |

AWS 액세스 키, 시크릿 키, PEM 파일은 GitHub에 등록하거나 저장소에 커밋하지 않습니다.

## 4. API 도메인과 HTTPS 연결

프론트엔드와 API 도메인을 분리하기 위해 백엔드는 `api.flout.shop`을 사용합니다. 도메인을 구매한 계정의 가비아 DNS 관리 화면에서 다음 레코드를 등록합니다.

| 타입 | 호스트 | 값 | TTL |
| --- | --- | --- | --- |
| `A` | `api` | `43.200.201.210` | `600` |

DNS가 전파되어 아래 명령이 EC2의 탄력적 IP를 반환하는지 먼저 확인합니다.

```bash
dig +short api.flout.shop
```

EC2 보안 그룹은 HTTP 80과 HTTPS 443 인바운드를 허용합니다. 애플리케이션 포트 8080은 외부에 열지 않습니다. `/etc/nginx/sites-available/flout`을 다음과 같이 설정합니다.

```nginx
server {
    listen 80;
    listen [::]:80;
    server_name api.flout.shop;

    location / {
        client_max_body_size 80m;
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

설정 문법을 검사한 뒤 NGINX를 다시 불러옵니다.

```bash
sudo ln -sf /etc/nginx/sites-available/flout /etc/nginx/sites-enabled/flout
sudo nginx -t
sudo systemctl reload nginx
```

`api.flout.shop`의 DNS 조회와 HTTP 접속이 모두 성공한 다음에만 Let's Encrypt 인증서를 발급합니다.

```bash
sudo snap install --classic certbot
sudo /snap/bin/certbot --nginx -d api.flout.shop --redirect
sudo /snap/bin/certbot renew --dry-run
curl --fail https://api.flout.shop/actuator/health
```

## 5. 프론트엔드 CORS 설정

브라우저에서 API를 호출할 프론트엔드 출처를 `/etc/flout/flout.env`에 쉼표로 구분해 등록합니다. URL 끝에는 `/`를 붙이지 않습니다.

```dotenv
CORS_ALLOWED_ORIGINS=https://flout.shop,https://www.flout.shop
```

로컬 또는 별도 프리뷰 프론트엔드가 실제로 API를 호출해야 할 때만 해당 출처를 명시적으로 추가합니다. 와일드카드(`*`)는 사용하지 않습니다. 환경변수를 수정한 뒤 서비스를 재시작하고 CORS preflight를 확인합니다.

```bash
sudo systemctl restart flout
curl -i -X OPTIONS https://api.flout.shop/api/auth/login \
  -H 'Origin: https://flout.shop' \
  -H 'Access-Control-Request-Method: POST'
```

응답에 `Access-Control-Allow-Origin: https://flout.shop`이 포함되어야 합니다.

## 수동 상태 확인

```bash
sudo systemctl status flout --no-pager
sudo journalctl -u flout -n 100 --no-pager
curl --fail http://127.0.0.1:8080/actuator/health
```

현재 JAR와 직전 백업 JAR는 다음 위치에 있습니다.

```text
/opt/flout/flout-server.jar
/opt/flout/flout-server.jar.previous
```

## 수동 롤백

자동 롤백까지 실패한 경우 EC2에서 다음을 실행합니다.

```bash
sudo cp /opt/flout/flout-server.jar.previous /opt/flout/flout-server.jar
sudo systemctl restart flout
curl --fail http://127.0.0.1:8080/actuator/health
```
