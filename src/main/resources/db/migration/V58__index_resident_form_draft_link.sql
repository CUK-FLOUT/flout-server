CREATE INDEX idx_form_draft_linked
    ON resident_form_drafts (draft_type, linked_id, form_state);
