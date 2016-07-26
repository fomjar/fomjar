-- cdb命令映射表
drop table if exists tbl_instruction;
create table tbl_instruction (
    i_inst  integer,
    c_mode  char(5),      -- st:执行sql语句 sp:执行存储过程
    i_out   tinyint,      -- 出参个数
    c_sql   varchar(255)  -- 格式：先出参，后入参，出参以英文的?代替，入参使用$打头，入参名称必须与消息中的参数名称完全相同
);

-- 平台账户信息
drop table if exists tbl_platform_account;
create table tbl_platform_account (
    i_paid      integer,        -- 平台账户ID
    c_user      varchar(32),    -- 用户名
    c_pass      varchar(32),    -- 密码
    c_name      varchar(32),    -- 姓名
    c_mobile    varchar(20),    -- 手机
    c_email     varchar(32),    -- 邮箱
    t_birth     date,           -- 出生日期
    i_cash      decimal(9, 2),  -- 现金（可退的）
    i_coupon    decimal(9, 2),  -- 优惠券（不可退）
    t_create    datetime        -- 创建时间
);

-- 平台账户与渠道账户的关系
drop table if exists tbl_platform_account_map;
create table tbl_platform_account_map (
    i_paid      integer,    -- 平台账户ID
    i_caid      integer     -- 渠道账户ID
);

-- 金额操作
drop table if exists tbl_platform_account_money;
create table tbl_platform_account_money (
    i_paid      integer,        -- 平台账户ID
    c_remark    varchar(64),    -- 备注
    t_time      datetime,       -- 操作时间
    i_type      tinyint,        -- 0-消费结算，1-现金操作，2-优惠券操作
    i_base      decimal(9, 2),  -- 基准值
    i_money     decimal(9, 2)   -- 变化值
);

-- 游戏账户
drop table if exists tbl_game_account;
create table tbl_game_account (
    i_gaid      integer,        -- 游戏账户ID，对应产品实例
    c_user      varchar(32),    -- 用户名
    c_pass_a    varchar(32),    -- 密码A
    c_pass_b    varchar(32),    -- 密码B
    c_pass_curr varchar(32),    -- 当前密码
    t_birth     date,           -- 出生日期
    t_create    datetime        -- 创建时间
);

drop table if exists tbl_game_account_game;
create table tbl_game_account_game (
    i_gaid  integer,    -- 游戏ID
    i_gid   integer     -- 游戏账户ID
);

-- 当前租赁状态
drop table if exists tbl_game_account_rent;
create table tbl_game_account_rent(
    i_gaid      integer,    -- 游戏账号ID
    i_type      tinyint,    -- 租赁类型：0-A租，1-B租
    i_caid      integer,    -- 渠道账户账户ID
    i_state     tinyint,    -- 租赁状态: 0-空闲，1-租用，2-锁定
    t_change    datetime    -- 变化时间
);

-- 平台账户流水
drop table if exists tbl_game_account_rent_history;
create table tbl_game_account_rent_history (
    i_gaid      integer,    -- 游戏账号ID
    i_type      tinyint,    -- 租赁类型：0-A租，1-B租
    i_caid      integer,    -- 渠道账户ID
    i_state     tinyint,    -- 变化前的状态
    t_change    datetime    -- 变化时间
);

-- 游戏信息表
drop table if exists tbl_game;
create table tbl_game (
    i_gid           integer,        -- 游戏ID
    c_platform      varchar(16),    -- 游戏和账号所属平台：PS4、XBOX ONE、PS5、XBOX TWO、PS3、XBOX 360
    c_country       varchar(32),    -- 国家
    c_url_icon      varchar(128),   -- 图标URL
    c_url_poster    varchar(128),   -- 海报URL
    c_url_buy       varchar(128),   -- 采购网址
    t_sale          date,           -- 发售日期
    c_name_zh       varchar(64),    -- 中文名
    c_name_en       varchar(64)     -- 英文名
);

-- 订单
drop table if exists tbl_order;
create table tbl_order (
    i_oid       integer,        -- 订单ID
    i_platform  tinyint,        -- 订单来源平台类型：0-淘宝 1-微信
    i_caid      integer,        -- 渠道账户ID
    t_open      datetime,       -- 打开时间
    t_close     datetime        -- 关闭时间
);

-- 商品
drop table if exists tbl_commodity;
create table tbl_commodity (
    i_oid       integer,        -- 订单ID
    i_csn       integer,        -- 商品序列号
    c_remark    varchar(64),    -- 备注
    i_price     decimal(9, 2),  -- 单价
    i_count     integer,        -- 数量
    t_begin     datetime,       -- 购买/租用开始时间
    t_end       datetime,       -- 购买/租用结束时间
    i_expense   decimal(9, 2),  -- 商品费用
    c_arg0      varchar(64),    -- 参数0
    c_arg1      varchar(64),    -- 参数1
    c_arg2      varchar(64),    -- 参数2
    c_arg3      varchar(64),    -- 参数3
    c_arg4      varchar(64),    -- 参数4
    c_arg5      varchar(64),    -- 参数5
    c_arg6      varchar(64),    -- 参数6
    c_arg7      varchar(64),    -- 参数7
    c_arg8      varchar(64),    -- 参数8
    c_arg9      varchar(64)     -- 参数9
);

-- 渠道账户信息
drop table if exists tbl_channel_account;
create table tbl_channel_account (
    i_caid      integer,        -- 渠道账户ID
    c_user      varchar(32),    -- 用户名
    i_channel   tinyint,        -- 渠道：0-淘宝 1-微信 2-支付宝
    c_name      varchar(32),    -- 姓名
    i_gender    tinyint,        -- 性别：0-女 1-男 2-人妖
    c_phone     varchar(20),    -- 电话
    c_address   varchar(100),   -- 地址
    c_zipcode   varchar(10),    -- 邮编
    t_birth     date,           -- 生日
    t_create    datetime        -- 创建时间
);

-- 游戏价格管理
drop table if exists tbl_game_rent_price;
create table tbl_game_rent_price (
    i_gid   integer,
    i_type  tinyint,    -- 租赁类型：0-A租，1-B租
    i_price decimal(4, 2)
);

-- TAG管理
drop table if exists tbl_tag;
create table tbl_tag (
    i_type      tinyint,    -- 0-game
    i_instance  integer,    -- 标记实例
    c_tag       varchar(16) -- tag值
);

-- 工单管理
drop table if exists tbl_ticket;
create table tbl_ticket (
    i_tid       integer,        -- 工单编号
    i_caid      integer,        -- 渠道用户ID
    t_open      datetime,       -- 打开时间
    t_close     datetime,       -- 关闭时间
    i_type      tinyint,        -- 0-退款申请，1-意见建议，2-通知提醒，3-预约预定，4-备忘纪要
    c_title     varchar(64),    -- 工单标题
    c_content   varchar(512),   -- 工单内容
    i_state     tinyint,        -- 工单状态：0-open, 1-close, 2-cancel
    c_result    varchar(64)     -- 处理结果
);

-- 通知
drop table if exists tbl_notification;
create table tbl_notification (
    i_nid       integer,        -- 通知编号
    i_caid      integer,        -- 被通知的用户
    c_content   varchar(64),    -- 通知内容
    t_create    datetime        -- 创建通知的时间
);

