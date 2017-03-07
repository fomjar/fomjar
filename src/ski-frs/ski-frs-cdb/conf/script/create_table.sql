
-- cdb命令映射表
drop table if exists tbl_instruction;
create table tbl_instruction (
    i_inst  integer,
    c_mode  char(5),    -- st:执行sql语句 sp:执行存储过程
    i_out   tinyint,    -- 出参个数
    c_sql   text        -- 格式：先出参，后入参，出参以英文的?代替，入参使用$打头，入参名称必须与消息中的参数名称完全相同
);

-- 设备
drop table if exists tbl_dev;
create table tbl_dev (
    c_did   varchar(64),    -- 编号
    c_path  text,           -- 路径：市／区／卡点
    c_ip    varchar(20),    -- IP
    primary key(c_did)
);

-- 图片
drop table if exists tbl_pic;
create table tbl_pic (
    i_pid   integer     auto_increment, -- 图片编号
    c_did   varchar(64),                -- 设备编号
    c_name  varchar(64),                -- 名称
    t_time  datetime,                   -- 生成时间
    i_size  tinyint,                    -- 尺寸：0 - 大图(全图)，1 - 中图(半身)，2 - 小图(头像)
    i_type  tinyint,                    -- 类型：0 - 人物，1 - 汽车
    c_desc1 text,                       -- 描述1(面部特征向量)
    c_desc2 text,                       -- 描述2(外部特征，格式：上衣颜色=白色;眼镜;)
    c_desc3 text,                       -- 描述3
    primary key(i_pid)
);

-- 主体库
drop table if exists tbl_sub_lib;
create table tbl_sub_lib (
    i_slid  integer     auto_increment, -- 主体库编号
    c_name  varchar(64),                -- 主体库名称
    i_type  tinyint,                    -- 主体类型：0 - 人，1 - 汽车
    primary key(i_slid)
);

-- 主体 - 人
drop table if exists tbl_sub_person;
create table tbl_sub_person (
    i_spid      integer     auto_increment, -- 主体编号
    i_slid      integer,                    -- 主体库编号
    c_name      varchar(32),                -- 姓名
    i_gender    tinyint,                    -- 性别：0 - 女，1 - 男
    i_idcard    varchar(32),                -- 身份证号
    t_birth     date,                       -- 生日
    c_province  varchar(16),                -- 省份
    c_city      varchar(16),                -- 城市
    primary key(i_spid)
);

-- 主体关联图片 - 人
drop table if exists tbl_sub_person_pic;
create table tbl_sub_person_pic (
    i_spid  integer,    -- 主体编号
    i_pid   integer     -- 图片编号
);



