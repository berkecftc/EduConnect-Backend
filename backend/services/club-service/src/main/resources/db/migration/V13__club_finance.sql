CREATE TABLE club_db.club_budgets (
    id uuid PRIMARY KEY,
    club_id uuid NOT NULL REFERENCES club_db.clubs (id) ON DELETE CASCADE,
    request_id uuid NOT NULL UNIQUE REFERENCES club_db.club_approval_requests (id) ON DELETE CASCADE,
    academic_year integer NOT NULL,
    planned_income numeric(12, 2) NOT NULL CHECK (planned_income >= 0),
    planned_expense numeric(12, 2) NOT NULL CHECK (planned_expense >= 0),
    description text,
    prepared_by uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    approved_at timestamp with time zone
);

CREATE INDEX idx_club_budgets_year ON club_db.club_budgets (club_id, academic_year) WHERE approved_at IS NOT NULL;

CREATE TABLE club_db.club_sponsorships (
    id uuid PRIMARY KEY,
    club_id uuid NOT NULL REFERENCES club_db.clubs (id) ON DELETE CASCADE,
    request_id uuid NOT NULL UNIQUE REFERENCES club_db.club_approval_requests (id) ON DELETE CASCADE,
    sponsor_name varchar(200) NOT NULL,
    description text NOT NULL,
    cash_amount numeric(12, 2) CHECK (cash_amount >= 0),
    in_kind varchar(1000),
    starts_on date NOT NULL,
    ends_on date,
    document_object varchar(512),
    document_name varchar(255),
    prepared_by uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    approved_at timestamp with time zone,
    CONSTRAINT ck_club_sponsorships_period CHECK (ends_on IS NULL OR ends_on >= starts_on)
);

CREATE INDEX idx_club_sponsorships_club ON club_db.club_sponsorships (club_id, created_at DESC);

CREATE TABLE club_db.club_finance_entries (
    id uuid PRIMARY KEY,
    club_id uuid NOT NULL REFERENCES club_db.clubs (id) ON DELETE CASCADE,
    request_id uuid NOT NULL UNIQUE REFERENCES club_db.club_approval_requests (id) ON DELETE CASCADE,
    sponsorship_id uuid REFERENCES club_db.club_sponsorships (id) ON DELETE SET NULL,
    type varchar(10) NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    amount numeric(12, 2) NOT NULL CHECK (amount > 0),
    description varchar(500) NOT NULL,
    occurred_on date NOT NULL,
    academic_year integer NOT NULL,
    document_object varchar(512),
    document_name varchar(255),
    budget_exceeded boolean NOT NULL DEFAULT false,
    prepared_by uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    approved_at timestamp with time zone
);

CREATE INDEX idx_club_finance_entries_year ON club_db.club_finance_entries (club_id, academic_year, occurred_on DESC);

ALTER TABLE club_db.club_approval_requests
    DROP CONSTRAINT ck_club_approval_requests_type,
    ADD CONSTRAINT ck_club_approval_requests_type
        CHECK (type IN ('ROLE_CHANGE', 'RESIGNATION', 'ADVISOR_CHANGE', 'CLUB_CLOSURE', 'MEMBER_EXPULSION',
                        'CLUB_PROFILE_UPDATE', 'CLUB_LOGO_CHANGE', 'CLUB_ANNOUNCEMENT', 'CLUB_BUDGET',
                        'CLUB_FINANCE_ENTRY', 'CLUB_SPONSORSHIP'));

DROP INDEX club_db.ux_club_approval_requests_pending_club;
CREATE UNIQUE INDEX ux_club_approval_requests_pending_club
    ON club_db.club_approval_requests (club_id, type)
    WHERE status IN ('PENDING_PRESIDENT', 'PENDING_ADVISOR')
        AND type IN ('ADVISOR_CHANGE', 'CLUB_CLOSURE', 'CLUB_PROFILE_UPDATE', 'CLUB_LOGO_CHANGE', 'CLUB_BUDGET');
