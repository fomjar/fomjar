
-- cdb命令映射表
drop table if exists tbl_instruction;
create table tbl_instruction (
    i_inst  integer,
    c_mode  char(5),    -- st:执行sql语句 sp:执行存储过程
    i_out   tinyint,    -- 出参个数
    c_sql   text        -- 格式：先出参，后入参，出参以英文的?代替，入参使用$打头，入参名称必须与消息中的参数名称完全相同
);

-- 用户
drop table if exists tbl_user;
create table tbl_user (
    i_uid       integer,        -- 编号
    t_create    datetime,       -- 创建时间
    c_pass      varchar(16),    -- 密码
    c_phone     varchar(16),    -- 电话
    c_email     varchar(32),    -- 邮箱
    c_name      varchar(32),    -- 姓名
    c_cover     mediumtext,     -- 封面图
    primary key (i_uid)
);

-- 用户状态
drop table if exists tbl_user_state;
create table tbl_user_state (
    i_uid       integer,        -- 编号
    i_state     tinyint,        -- 状态：0 - 离线; 1 - 在线
    t_change    datetime,       -- 变化时间
    i_terminal  tinyint,        -- 终端：1 - web; 2 - app
    c_token     varchar(64),    -- 口令
    c_location  varchar(64)     -- 位置
);

-- 用户状态历史
drop table if exists tbl_user_state_history;
create table tbl_user_state_history (
    i_uid       integer,        -- 编号
    i_state     tinyint,        -- 状态：0 - 离线; 1 - 在线
    t_change    datetime,       -- 变化时间
    i_terminal  tinyint,        -- 终端：1 - m-web; 2 - pc-web; 3 - app
    c_token     varchar(64),    -- 口令
    c_location  varchar(64)     -- 位置
);

