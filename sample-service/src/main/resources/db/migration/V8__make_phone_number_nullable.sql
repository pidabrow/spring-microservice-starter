-- Make phone_number nullable to support registration flows that don't collect a phone number
ALTER TABLE users ALTER COLUMN phone_number DROP NOT NULL;
