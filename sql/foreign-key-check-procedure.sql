DELIMITER $$

DROP PROCEDURE IF EXISTS ANALYZE_INVALID_FOREIGN_KEYS$$

CREATE
    PROCEDURE `ANALYZE_INVALID_FOREIGN_KEYS`(
        checked_database_name VARCHAR(64), 
        checked_table_name VARCHAR(64), 
        temporary_result_table ENUM('Y', 'N'))

    LANGUAGE SQL
    NOT DETERMINISTIC
    READS SQL DATA

    BEGIN
        DECLARE TABLE_SCHEMA_VAR VARCHAR(64);
        DECLARE TABLE_NAME_VAR VARCHAR(64);
        DECLARE COLUMN_NAME_VAR VARCHAR(64); 
        DECLARE CONSTRAINT_NAME_VAR VARCHAR(64);
        DECLARE REFERENCED_TABLE_SCHEMA_VAR VARCHAR(64);
        DECLARE REFERENCED_TABLE_NAME_VAR VARCHAR(64);
        DECLARE REFERENCED_COLUMN_NAME_VAR VARCHAR(64);
        DECLARE KEYS_SQL_VAR VARCHAR(1024);

        DECLARE done INT DEFAULT 0;

        DECLARE foreign_key_cursor CURSOR FOR
            SELECT
                `TABLE_SCHEMA`,
                `TABLE_NAME`,
                `COLUMN_NAME`,
                `CONSTRAINT_NAME`,
                `REFERENCED_TABLE_SCHEMA`,
                `REFERENCED_TABLE_NAME`,
                `REFERENCED_COLUMN_NAME`
            FROM 
                information_schema.KEY_COLUMN_USAGE 
            WHERE 
                `CONSTRAINT_SCHEMA` LIKE checked_database_name AND
                `TABLE_NAME` LIKE checked_table_name AND
                `REFERENCED_TABLE_SCHEMA` IS NOT NULL;

        DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

        IF temporary_result_table = 'N' THEN
            DROP TEMPORARY TABLE IF EXISTS INVALID_FOREIGN_KEYS;
            DROP TABLE IF EXISTS INVALID_FOREIGN_KEYS;

            CREATE TABLE INVALID_FOREIGN_KEYS(
                `TABLE_SCHEMA` VARCHAR(64), 
                `TABLE_NAME` VARCHAR(64), 
                `COLUMN_NAME` VARCHAR(64), 
                `CONSTRAINT_NAME` VARCHAR(64),
                `REFERENCED_TABLE_SCHEMA` VARCHAR(64),
                `REFERENCED_TABLE_NAME` VARCHAR(64),
                `REFERENCED_COLUMN_NAME` VARCHAR(64),
                `INVALID_KEY_COUNT` INT,
                `INVALID_KEY_SQL` VARCHAR(1024)
            );
        ELSEIF temporary_result_table = 'Y' THEN
            DROP TEMPORARY TABLE IF EXISTS INVALID_FOREIGN_KEYS;
            DROP TABLE IF EXISTS INVALID_FOREIGN_KEYS;

            CREATE TEMPORARY TABLE INVALID_FOREIGN_KEYS(
                `TABLE_SCHEMA` VARCHAR(64), 
                `TABLE_NAME` VARCHAR(64), 
                `COLUMN_NAME` VARCHAR(64), 
                `CONSTRAINT_NAME` VARCHAR(64),
                `REFERENCED_TABLE_SCHEMA` VARCHAR(64),
                `REFERENCED_TABLE_NAME` VARCHAR(64),
                `REFERENCED_COLUMN_NAME` VARCHAR(64),
                `INVALID_KEY_COUNT` INT,
                `INVALID_KEY_SQL` VARCHAR(1024)
            );
        END IF;


        OPEN foreign_key_cursor;
        foreign_key_cursor_loop: LOOP
            FETCH foreign_key_cursor INTO 
            TABLE_SCHEMA_VAR, 
            TABLE_NAME_VAR, 
            COLUMN_NAME_VAR, 
            CONSTRAINT_NAME_VAR, 
            REFERENCED_TABLE_SCHEMA_VAR, 
            REFERENCED_TABLE_NAME_VAR, 
            REFERENCED_COLUMN_NAME_VAR;
            IF done THEN
                LEAVE foreign_key_cursor_loop;
            END IF;


            SET @from_part = CONCAT('FROM ', '`', TABLE_SCHEMA_VAR, '`.`', TABLE_NAME_VAR, '`', ' AS REFERRING ', 
                 'LEFT JOIN `', REFERENCED_TABLE_SCHEMA_VAR, '`.`', REFERENCED_TABLE_NAME_VAR, '`', ' AS REFERRED ', 
                 'ON (REFERRING', '.`', COLUMN_NAME_VAR, '`', ' = ', 'REFERRED', '.`', REFERENCED_COLUMN_NAME_VAR, '`', ') ', 
                 'WHERE REFERRING', '.`', COLUMN_NAME_VAR, '`', ' IS NOT NULL ',
                 'AND REFERRED', '.`', REFERENCED_COLUMN_NAME_VAR, '`', ' IS NULL');
            SET @full_query = CONCAT('SELECT COUNT(*) ', @from_part, ' INTO @invalid_key_count;');
            PREPARE stmt FROM @full_query;

            EXECUTE stmt;
            IF @invalid_key_count > 0 THEN
                INSERT INTO 
                    INVALID_FOREIGN_KEYS 
                SET 
                    `TABLE_SCHEMA` = TABLE_SCHEMA_VAR, 
                    `TABLE_NAME` = TABLE_NAME_VAR, 
                    `COLUMN_NAME` = COLUMN_NAME_VAR, 
                    `CONSTRAINT_NAME` = CONSTRAINT_NAME_VAR, 
                    `REFERENCED_TABLE_SCHEMA` = REFERENCED_TABLE_SCHEMA_VAR, 
                    `REFERENCED_TABLE_NAME` = REFERENCED_TABLE_NAME_VAR, 
                    `REFERENCED_COLUMN_NAME` = REFERENCED_COLUMN_NAME_VAR, 
                    `INVALID_KEY_COUNT` = @invalid_key_count,
                    `INVALID_KEY_SQL` = CONCAT('SELECT ', 
                        'REFERRING.', '`', COLUMN_NAME_VAR, '` ', 'AS "Invalid: ', COLUMN_NAME_VAR, '", ', 
                        'REFERRING.* ', 
                        @from_part, ';');
            END IF;
            DEALLOCATE PREPARE stmt; 

        END LOOP foreign_key_cursor_loop;
    END$$

DELIMITER ;
