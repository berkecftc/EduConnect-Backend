ALTER TABLE club_db.clubs
    ADD COLUMN category varchar(30),
    ADD COLUMN contact_email varchar(255),
    ADD COLUMN website_url varchar(255),
    ADD COLUMN instagram_url varchar(255),
    ADD COLUMN x_url varchar(255),
    ADD COLUMN linkedin_url varchar(255),
    ADD CONSTRAINT ck_clubs_category
        CHECK (category IN ('ACADEMIC', 'SCIENCE_TECHNOLOGY', 'ARTS_CULTURE', 'SPORTS', 'SOCIAL_RESPONSIBILITY',
                            'HOBBY', 'CAREER', 'OTHER'));

CREATE INDEX idx_clubs_category ON club_db.clubs (category) WHERE status <> 'CLOSED';

CREATE TABLE club_db.club_profile_changes (
    request_id uuid PRIMARY KEY REFERENCES club_db.club_approval_requests (id) ON DELETE CASCADE,
    about text,
    category varchar(30),
    contact_email varchar(255),
    website_url varchar(255),
    instagram_url varchar(255),
    x_url varchar(255),
    linkedin_url varchar(255),
    logo_url varchar(1024),
    CONSTRAINT ck_club_profile_changes_category
        CHECK (category IN ('ACADEMIC', 'SCIENCE_TECHNOLOGY', 'ARTS_CULTURE', 'SPORTS', 'SOCIAL_RESPONSIBILITY',
                            'HOBBY', 'CAREER', 'OTHER'))
);

ALTER TABLE club_db.club_approval_requests
    DROP CONSTRAINT ck_club_approval_requests_type,
    ADD CONSTRAINT ck_club_approval_requests_type
        CHECK (type IN ('ROLE_CHANGE', 'RESIGNATION', 'ADVISOR_CHANGE', 'CLUB_CLOSURE', 'MEMBER_EXPULSION',
                        'CLUB_PROFILE_UPDATE', 'CLUB_LOGO_CHANGE'));

DROP INDEX club_db.ux_club_approval_requests_pending_club;
CREATE UNIQUE INDEX ux_club_approval_requests_pending_club
    ON club_db.club_approval_requests (club_id, type)
    WHERE status IN ('PENDING_PRESIDENT', 'PENDING_ADVISOR')
        AND type IN ('ADVISOR_CHANGE', 'CLUB_CLOSURE', 'CLUB_PROFILE_UPDATE', 'CLUB_LOGO_CHANGE');
