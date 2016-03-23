delete from tbl_cmd_map where i_cmd = (conv(00000300, 16, 10) + 0);
insert into tbl_cmd_map values((conv(00000300, 16, 10) + 0), 'sp', 2, "sp_update_order(?, ?,$i_coid, $i_channel, $'c_caid', $'t_place',$i_prod_price,$i_prod_num,$'c_name_cns',$i_prod_type)");
DELIMITER // 
DROP PROCEDURE IF EXISTS `ski`.`sp_update_order` //
CREATE PROCEDURE `ski`.`sp_update_order`(
    out   out_i_code             BIGINT,
    inout out_c_desc             blob,
    in    in_i_coid              integer,
    in    in_i_channel           integer,
    in    in_c_caid              varchar(64),
    in    in_t_place             datetime,
    in    in_i_prod_price        decimal(7, 2),
    in    in_i_prod_num          integer,
    in    in_c_name_cns          varchar(64),
    in    in_i_prod_type         integer
)
BEGIN
    declare new_c_poid      varchar(64);
    declare new_c_takeInfo  varchar(64);
    declare i_gaid_tmp integer default 0;
    declare i integer default 0; 
    declare i_gid_tmp  integer default 0;
    declare i_pid_tmp  integer default 0;
    declare i_error    BIGINT default 0;
    declare i_state_after integer default 0;
    select i_error;
    set out_i_code = 0;
    
    select i_gid 
      into i_gid_tmp 
      from tbl_game 
     where c_name_cns =  in_c_name_cns;
     
    select i_pid 
      into i_pid_tmp 
      from tbl_product 
     where i_inst_type = i_gid_tmp;
     
    select i_gid_tmp,i_pid_tmp;
    call sp_generate_poid(out_i_code,out_c_desc,new_c_poid);
    set i_error = i_error + out_i_code;
    
    START TRANSACTION;  
    select i_error;
    call sp_insert_tbl_order(out_i_code,out_c_desc,new_c_poid,in_i_coid,in_i_channel,in_c_caid,in_t_place);
    set i_error = i_error + out_i_code;
    select i_error;
    select in_i_prod_num;
    
    while i < in_i_prod_num do  
        /*sp_get_accout��ȡ��Ϸ���͵���Ϸ�˻���Ϣ*/
        call sp_get_accout(out_i_code,out_c_desc,i_gaid_tmp,i_gid_tmp,in_i_prod_type);
        set i_error = i_error + out_i_code;
        select  i,i_gaid_tmp,i_error,out_i_code;
        /*��������룬���ݶ����ź���Ϸ�˻�ID����Ψһ*/
        call sp_generate_takInfo(out_i_code,out_c_desc,new_c_poid,i_gaid_tmp,new_c_takeInfo);
        select  i,i_gaid_tmp,i_error,out_i_code,new_c_takeInfo;
        /*insert��tbl_order_product��*/
        call sp_insert_tbl_order_product(out_i_code,out_c_desc,new_c_poid,i_pid_tmp,in_i_prod_type,in_c_name_cns,in_i_prod_price,0,new_c_takeInfo,i_gid_tmp,i_gaid_tmp);
        set i_error = i_error + out_i_code;
        
        /*����Ժ��޸�I_RENT״̬*/
        if in_i_prod_type = 0 then 
            /*����A����Ϸ״̬Ϊ���ɳ���״̬����Ϊ��ʱ���˺��Ѿ��������ȥ*/
            call  sp_update_to_ANotRent(out_i_code,out_c_desc,i_state_after,i_gaid_tmp);
            set i_error = i_error + out_i_code;
        /*����B����Ϸ���ʱ��Ϊ�Ѿ��۳�*/    
        elseif  in_i_prod_type = 1 then 
            call  sp_update_to_BAlreadyRent(out_i_code,out_c_desc,i_state_after,i_gaid_tmp);
            set i_error = i_error + out_i_code;
        
        end if;
        
        set i=i+1;  
    end while;  
    select i_error; -- ��ӡ��ERROR��Ϣ����Ļ
    IF i_error<>0 THEN  
        SELECT "RollBack";
        ROLLBACK;  
    ELSE  
        SELECT "COMMINT";
        COMMIT;  
    END IF; 
END //  
DELIMITER ; 


