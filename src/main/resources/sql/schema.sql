-- PostgreSQL schema (matches application SQL)

CREATE TABLE IF NOT EXISTS INTERVIEW_ROUND (
    round_id SERIAL PRIMARY KEY,
    round_name VARCHAR(50) UNIQUE NOT NULL,
    sequence_order INT UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS APPLICANT (
    applicant_id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    email VARCHAR(50) UNIQUE NOT NULL,
    profile_pic_path VARCHAR(255),
    current_stage_id INT DEFAULT 1 REFERENCES INTERVIEW_ROUND (round_id),
    overall_status VARCHAR(20) DEFAULT 'Active'
);

CREATE TABLE IF NOT EXISTS INTERVIEW_PROGRESS (
    progress_id SERIAL PRIMARY KEY,
    applicant_id INT REFERENCES APPLICANT (applicant_id) ON DELETE CASCADE,
    round_id INT REFERENCES INTERVIEW_ROUND (round_id),
    compatibility_score INT CHECK (compatibility_score BETWEEN 0 AND 10),
    status VARCHAR(20) CHECK (status IN ('Passed', 'Failed', 'Pending')),
    feedback TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (applicant_id, round_id)
);

-- Seed stages when empty (sequence_order drives pipeline order)
INSERT INTO INTERVIEW_ROUND (round_name, sequence_order)
SELECT v.name, v.ord
FROM (VALUES
    ('Screening', 1),
    ('Technical', 2),
    ('HR', 3),
    ('Offer', 4)
) AS v(name, ord)
WHERE NOT EXISTS (SELECT 1 FROM INTERVIEW_ROUND);
