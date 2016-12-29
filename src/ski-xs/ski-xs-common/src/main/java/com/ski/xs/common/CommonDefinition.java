package com.ski.xs.common;

public class CommonDefinition {
    
    public static final class ISIS {
        public static final int INST_APPLY_AUTHORIZE    = 0x00001001;
        
        public static final int INST_UPDATE_USER            = 0x00002001;
        public static final int INST_UPDATE_ARTICLE         = 0x00002002;
        public static final int INST_UPDATE_PARAGRAPH       = 0x00002003;
        public static final int INST_UPDATE_PARAGRAPH_DEL   = 0x00002004;
        public static final int INST_UPDATE_ELEMENT         = 0x00002005;
        public static final int INST_UPDATE_ELEMENT_DEL     = 0x00002006;
        public static final int INST_UPDATE_TAG             = 0x00002007;
        public static final int INST_UPDATE_TAG_DEL         = 0x00002008;
    }
    
    public static final class CODE {
        public static final int CODE_SUCCESS            = 0x00000000;
        public static final int CODE_ERROR              = 0xFFFFFFFF;
        public static final int CODE_INTERNAL_ERROR     = 0x00000001;
        public static final int CODE_ILLEGAL_INST       = 0x00000002;
        public static final int CODE_ILLEGAL_ARGS       = 0x00000003;
        public static final int CODE_UNAUTHORIZED       = 0x00000004;
    }

}