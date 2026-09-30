-- Run once before deploying the security-hardening branch.
-- Remove duplicate collection rows before adding the unique keys.
-- Keep a database backup and apply this through the normal migration process.

ALTER TABLE `user` MODIFY COLUMN `password` VARCHAR(255) NOT NULL;

ALTER TABLE `book_collection`
    ADD UNIQUE KEY `uq_book_collection_user_book` (`user_id`, `book_id`);
ALTER TABLE `pre_collection`
    ADD UNIQUE KEY `uq_pre_collection_user_pre` (`user_id`, `pre_id`);
ALTER TABLE `post_collection`
    ADD UNIQUE KEY `uq_post_collection_user_post` (`user_id`, `post_id`);
