DELIMITER // 
DROP PROCEDURE IF EXISTS `ski`.`sp_generate_poid` //
CREATE PROCEDURE `ski`.`sp_generate_poid`(
    out   out_i_code             BIGINT,
    inout out_c_desc             blob,
    out   newOrderNo             varchar(64)
     )    
BEGIN    
  DECLARE currentDate varCHAR (15) ;-- ��ǰ����,�п��ܰ���ʱ����     
  DECLARE maxNo INT DEFAULT 0 ; -- ��������������������Ķ�����ŵ���ˮ�����5λ���磺SH2013011000002��maxNo=2     
  DECLARE oldOrderNo VARCHAR (64) DEFAULT '' ;-- ��������������������Ķ������     
  DECLARE orderNamePre char (2) default 'vc';
-- ����������ʱ�����ɶ������     
  SELECT 
     DATE_FORMAT(NOW(), '%Y%m%d%H%i') INTO currentDate ;-- ������ʽ��ǰ׺+������ʱ��+��ˮ��,�磺SH20130110100900005      
      
  SELECT IFNULL(c_poid, '') INTO oldOrderNo     
  FROM tbl_order     
  WHERE SUBSTRING(c_poid, 3, 12) = currentDate     
    AND SUBSTRING(c_poid, 1, 2) = orderNamePre
  ORDER BY CONVERT(SUBSTRING(c_poid, -5), DECIMAL) DESC LIMIT 1 ; -- �ж���ʱֻ��ʾ�����������һ��  
     
  select  oldOrderNo;
  
  IF oldOrderNo != '' THEN     
    SET maxNo = CONVERT(SUBSTRING(oldOrderNo, -5), DECIMAL) ;-- SUBSTRING(oldOrderNo, -5)��������������Ϊ������ȡ���������5λ     
  END IF ;    
  
  SELECT 
    CONCAT(orderNamePre, currentDate,  LPAD((maxNo + 1), 5, '0')) INTO newOrderNo ; -- LPAD((maxNo + 1), 5, '0')���������5λ������0������     
    
  SELECT     
    newOrderNo ; 

   set out_i_code= 0;
   set out_c_desc = "CODE_SUCCESS";       
END //  
DELIMITER ; 