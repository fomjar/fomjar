-- 修改租赁实例状态
delimiter //
drop function if exists fn_update_commodity // 
create function fn_update_commodity (
    oid       integer,        -- 订单ID
    csn       integer,        -- 商品序列号
    remark    varchar(64),    -- 备注
    price     decimal(9, 2),  -- 单价
    count     integer,        -- 数量
    _begin    datetime,       -- 购买/租用开始时间
    _end      datetime,       -- 购买/租用结束时间
    expense   decimal(9, 2),  -- 商品费用
    arg0      varchar(64),    -- 参数0
    arg1      varchar(64),    -- 参数1
    arg2      varchar(64),    -- 参数2
    arg3      varchar(64),    -- 参数3
    arg4      varchar(64),    -- 参数4
    arg5      varchar(64),    -- 参数5
    arg6      varchar(64),    -- 参数6
    arg7      varchar(64),    -- 参数7
    arg8      varchar(64),    -- 参数8
    arg9      varchar(64)     -- 参数9
)
returns integer
begin
    declare di_code integer     default -1;
    declare dc_desc mediumblob  default null;
    declare di_caid integer     default -1;

    select i_caid
      into di_caid
      from tbl_order
     where i_oid = oid;

    if _begin is not null then
        call sp_update_game_account_rent(di_code,
                dc_desc,
                conv(oper_arg1, 16, 10),
                (case oper_arg2 when 'A' then 0 when 'B' then 1 else -1 end),
                di_caid,
                1);
    end if;

    if _end is not null then
        call sp_update_game_account_rent(di_code,
                dc_desc,
                conv(oper_arg1, 16, 10),
                (case oper_arg2 when 'A' then 0 when 'B' then 1 else -1 end),
                di_caid,
                0);
    end if;

    return di_code;
end //
delimiter ;
