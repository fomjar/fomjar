package com.ski.stub.bean;

public class BeanGameAccount {
    
    public BeanGameAccount(String line) {
        String[] fields = line.split("\t", -1);
        this.i_gid          = Integer.parseInt(fields[0], 16);
        this.i_gaid         = Integer.parseInt(fields[1], 16);
        this.c_user         = fields[2];
        this.c_pass_a       = fields[3];
        this.c_pass_b       = fields[4];
        this.c_pass_curr    = fields[5];
        this.t_birth        = fields[6];
    }

    public int      i_gid;
    public int      i_gaid;
    public String   c_user;
    public String   c_pass_a;
    public String   c_pass_b;
    public String   c_pass_curr;
    public String   t_birth;
    
}
