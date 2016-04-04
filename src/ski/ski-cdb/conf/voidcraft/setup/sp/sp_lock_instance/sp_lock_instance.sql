delete from tbl_instruction where i_inst = (conv(00002201, 16, 10) + 0);
insert into tbl_instruction values((conv(00002201, 16, 10) + 0), 'sp', 2, "sp_lock_instance(?,?,'$user',$instance)");

delimiter //
drop procedure if exists sp_lock_instance //
create procedure sp_lock_instance (
    out i_code   integer,
    out c_desc   mediumblob,
    in  user     varchar(64),
    in  instance integer
)
comment '����ʵ������������������'
label_pro:begin
    declare i_prod_type integer default -1;

    select p.i_prod_type
      into i_prod_type
      from tbl_order o, tbl_order_product p
     where o.c_poid = p.c_poid
       and o.c_caid = user
       and p.i_prod_inst = instance;

    case i_prod_type
        -- PS4��ϷA����
        when 0 then
                call sp_lock_game_account(i_code, c_desc, instance);
        -- PS4��ϷB����
        when 1 then
                call sp_lock_game_account(i_code, c_desc, instance);
        else
                set i_code = -1;
                set c_desc = 'error user or instance';
    end case;

    declare i_case_status   integer;
    declare i_state_after   integer DEFAULT 0;
    declare c_desc_temp varchar(32);
    declare i_gaid_temp     integer  DEFAULT 0;
    declare i_change_fsm    integer DEFAULT 0;
    /*ʹ����������м����ָ����в���*/
    start transaction; 
    /*��ʼ������*/
    set c_desc = '';
    set i_code = 0;
    /*У��CAID��instance*/
    call sp_get_gaid_by_caid(i_code,c_desc,i_gaid_temp,user);
    
    select instance,user;
    if i_gaid_temp <> instance then
       select conv(F0000102, 16, 10) into i_code;
       set c_desc = "ILLEGAL_ARGUMENT.";   
       select c_desc;
       leave label_pro;
    end if;
    
    select i_rent 
      into i_case_status
      from tbl_game_account_rent 
     where i_gaid = instance;
      
    select i_case_status,i_gaid_temp;
    /*------------------------------------------------------------- 
    ��ӡ״̬��ǰ�ˣ����ں����߼�����
    select concat(c_desc,"From i_rent ") into c_desc;
    select convert(i_case_status USING ascii) into c_desc_temp;
    select concat(c_desc,c_desc_temp) into c_desc;
    select c_desc;
    -------------------------------------------------------------*/
    
     case i_case_status  

        /*A����B����*/
        when 10 then 
            call sp_update_to_ANotRentBNotRent(i_code,c_desc,i_state_after,i_gaid_temp); 
            select c_desc;
            set i_change_fsm = 1;
            
        /*A����B����*/
        when 11 then   
            call sp_update_to_ANotRentBAlreadyRent(i_code,c_desc,i_state_after,i_gaid_temp); 
            select c_desc;
            set i_change_fsm = 1;
            
        /*�쳣�����*/
        when 6 then   
            set i_code = 4026532098;/*ERROR_DB_OPERATE_FAILED = 0xF0000102; // ���ݿ��˻��쳣״̬*/
          
        /*�쳣�˺�*/
        when 7 then   
            set i_code = 4026532098;/*ERROR_DB_OPERATE_FAILED = 0xF0000102; // ���ݿ��˻��쳣״̬*/
           
        else   
            set i_code = 0;
          
    end case;  
    
    /*��ӡ��ǰ��c_desc����Ļ*/
    select c_desc;
    select i_change_fsm;
    select i_code;
  
    
    if i_code = 0 then
        if i_change_fsm = 1 then 
            call sp_update_tbl_journal_game_account(i_gaid_temp,user,NOW(),i_case_status,i_state_after,1);
        end if;
        commit;
    else
        rollback;
    end if;

end;
//
delimiter ;
