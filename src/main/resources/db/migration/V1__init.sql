-- Required for the GiST exclusion constraint below: it adds GiST operator
-- classes for plain equality (=), which the constraint needs to combine
-- "same owner" with the tstzrange overlap check in a single GiST index.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE app_user (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_user_email UNIQUE (email)
);

CREATE TABLE time_slot (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id   UUID         NOT NULL REFERENCES app_user (id),
    start_time TIMESTAMPTZ  NOT NULL,
    end_time   TIMESTAMPTZ  NOT NULL,
    status     VARCHAR(10)  NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_time_slot_status CHECK (status IN ('FREE', 'BUSY')),
    CONSTRAINT chk_time_slot_range CHECK (end_time > start_time),
    -- Belt-and-braces alongside @Version: makes cross-request double-booking
    -- of the same owner's time impossible at the DB level, regardless of
    -- which concurrent transaction the conflicting row came from.
    CONSTRAINT excl_time_slot_owner_overlap EXCLUDE USING gist (
        owner_id WITH =,
        tstzrange(start_time, end_time) WITH &&
    )
);

CREATE INDEX idx_time_slot_owner_start ON time_slot (owner_id, start_time);

CREATE TABLE meeting (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    start_time  TIMESTAMPTZ  NOT NULL,
    end_time    TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_meeting_range CHECK (end_time > start_time)
);

-- Added after `meeting` since the FK needs that table to exist first.
ALTER TABLE time_slot ADD COLUMN meeting_id UUID REFERENCES meeting (id);

CREATE INDEX idx_time_slot_meeting ON time_slot (meeting_id);

CREATE TABLE meeting_participant (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID        NOT NULL REFERENCES meeting (id),
    user_id    UUID        NOT NULL REFERENCES app_user (id),
    role       VARCHAR(11) NOT NULL,
    CONSTRAINT chk_meeting_participant_role CHECK (role IN ('OWNER', 'PARTICIPANT')),
    CONSTRAINT uq_meeting_participant UNIQUE (meeting_id, user_id)
);

CREATE INDEX idx_meeting_participant_user ON meeting_participant (user_id);
CREATE INDEX idx_meeting_participant_meeting ON meeting_participant (meeting_id);
