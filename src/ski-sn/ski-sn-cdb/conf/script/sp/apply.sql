

-- INST_APPLY_AUTHORIZE            = 0x00001001
delete from tbl_instruction where i_inst = (conv('00001001', 16, 10) + 0);
insert into tbl_instruction(
    i_inst,
    c_mode,
    i_out,
    c_sql
) values (
    (conv('00001001', 16, 10) + 0),
    'st',
    6,
    "select i_uid, t_create, c_phone, c_email, c_name, c_cover from tbl_user where c_phone = \"$phone\" and c_pass = \"$pass\""
);



