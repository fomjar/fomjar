delimiter // 
drop procedure if exists sp_query_channel_account_by_caid //   
create procedure sp_query_channel_account_by_caid (
    out i_code  integer,
    out c_desc  mediumblob,
    in  caid    varchar(64)
)  
begin
    declare c_caid      varchar(64)     default null;
    declare c_user      varchar(32)     default null;
    declare i_channel   tinyint         default -1;
    declare c_nick      varchar(32)     default null;
    declare i_gender    tinyint         default 2;
    declare c_phone     varchar(20)     default null;
    declare c_address   varchar(100)    default null;
    declare c_zipcode   varchar(10)     default null;
    declare t_birth     date            default null;

    declare done        integer default 0;
    declare rs          cursor for
                        select ca.c_caid, ca.c_user, ca.i_channel, ca.c_nick, ca.i_gender, ca.c_phone, ca.c_address, ca.c_zipcode, ca.t_birth
                          from tbl_channel_account ca
                         where ca.c_caid = caid
                         order by ca.c_caid;

    /* 异常处理 */
    declare continue handler for sqlstate '02000' set done = 1;

    /* 打开游标 */
    open rs;  
    /* 逐个取出当前记录i_gaid值*/
    fetch rs into c_caid, c_user, i_channel, c_nick, i_gender, c_phone, c_address, c_zipcode, t_birth;
    /* 遍历数据表 */
    while (done = 0) do
        if c_desc is null then set c_desc = '';
        else set c_desc = concat(c_desc, '\n');
        end if;

        set c_desc = concat(
                c_desc,
                ifnull(c_caid, ''),
                '\t',
                ifnull(c_user, ''),
                '\t',
                conv(i_channel, 10, 16),
                '\t',
                ifnull(c_nick, ''),
                '\t',
                conv(i_gender, 10, 16),
                '\t',
                ifnull(c_phone, ''),
                '\t',
                ifnull(c_address, ''),
                '\t',
                ifnull(c_zipcode, ''),
                '\t',
                ifnull(t_birth, '')
        );

        fetch rs into c_caid, c_user, i_channel, c_nick, i_gender, c_phone, c_address, c_zipcode, t_birth;
    end while;
    /* 关闭游标 */
    close rs;

    set i_code = 0;
end //  
delimiter ; 
