DELIMITER // 
DROP PROCEDURE IF EXISTS `ski`.`sp_insert_tbl_order_product` //
CREATE PROCEDURE `ski`.`sp_insert_tbl_order_product`(
    out   out_i_code             BIGINT,
    inout out_c_desc             blob,
    in   in_c_poid              varchar(64),    -- ƽ̨����ID
    in   in_i_pid               integer,        -- ��ƷID
    in   in_i_prod_type         integer,        -- ʵ������
    in   in_c_prod_name         varchar(64),    -- ����
    in   in_i_prod_price        decimal(7, 2),  -- ����
    in   in_i_state             tinyint,        -- ������Ʒ״̬��0-δ���� 1-�ѷ��� 2-����� 3-���˻�
    in   in_c_take_info         varchar(64),    -- ��ȡ��Ϣ
    in   in_i_inst_type         integer,        -- ʵ�����ͣ�����ϷID
    in   in_i_inst_id           integer         -- ʵ��ID������Ϸ�˻�ID
)
BEGIN
  
   insert into tbl_order_product(c_poid,i_pid,i_prod_type,c_prod_name,i_prod_price,i_state,c_take_info,i_inst_type,i_inst_id) 
                          VALUES (in_c_poid,in_i_pid,in_i_prod_type,in_c_prod_name,in_i_prod_price,in_i_state,in_c_take_info,in_i_inst_type,in_i_inst_id);
                          
   set out_i_code=0;
   set out_c_desc = "CODE_SUCCESS";   
END //  
DELIMITER ; 



