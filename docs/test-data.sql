DELIMITER $$

DROP PROCEDURE IF EXISTS SeedSeatsForConcert$$

CREATE PROCEDURE SeedSeatsForConcert(
    IN p_concert_id BIGINT,
    IN p_schedule_id BIGINT,
    IN p_start_seat INT,
    IN p_end_seat INT,
    IN p_price BIGINT
)
BEGIN
    DECLARE i INT DEFAULT p_start_seat;

    -- 트랜잭션 시작 (성능을 위해 묶어서 커밋)
START TRANSACTION;

WHILE i <= p_end_seat DO
            -- 중복 에러 방지를 위해 IGNORE 사용 (이미 있으면 무시)
            INSERT IGNORE INTO seats (concert_id, schedule_id, seat_number, price, status, version, created_at, updated_at)
            VALUES (p_concert_id, p_schedule_id, i, p_price, 'AVAILABLE', 0, NOW(6), NOW(6));
            SET i = i + 1;
END WHILE;

COMMIT;
END$$

DELIMITER ;


CALL SeedSeatsForConcert(1, 1, 1, 1000, 50000);
