/*����tbl_journal_game_account��*/
DELIMITER // 
DROP PROCEDURE IF EXISTS `ski`.`sp_update_tbl_journal_game_account` //
CREATE PROCEDURE `ski`.`sp_update_tbl_journal_game_account`(
    in i_gaid          integer,    -- ��Ϸ�˺�
    in in_c_caid       varchar(64),    -- �����˻�ID
    in t_change        datetime,   -- �仯ʱ��
    in i_state_before  tinyint,    -- �仯ǰ��״̬
    in i_state_after   tinyint,    -- �仯���״̬
    in i_cause         tinyint     -- ����0-�û����� 1-ϵͳ���� 2-�˹�ά������
)
BEGIN

    INSERT INTO tbl_journal_game_account(i_gaid,c_caid,t_change,i_state_before,i_state_after,i_cause) 
             VALUES (i_gaid,in_c_caid,t_change,i_state_before,i_state_after,i_cause);

END //  
DELIMITER ; 
