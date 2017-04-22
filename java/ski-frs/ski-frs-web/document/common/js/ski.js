
var ski = {};

(function($) {

ski.isis = {
    INST_AUTHORIZE              : 0x00000010,
    
    INST_UPDATE_PIC             : 0x00001000,
    INST_UPDATE_PIC_DEL         : 0x00001001,
    INST_UPDATE_SUB_LIB         : 0x00001010,
    INST_UPDATE_SUB_LIB_DEL     : 0x00001011,
    INST_UPDATE_SUB_MAN         : 0x00001020,
    INST_UPDATE_SUB_MAN_DEL     : 0x00001021,
    INST_UPDATE_SUB_MAN_PIC     : 0x00001022,
    INST_UPDATE_DEV             : 0x00001030,
    INST_UPDATE_DEV_DEL         : 0x00001031,
    
    INST_QUERY_PIC              : 0x00002000,
    INST_QUERY_PIC_BY_FV_I      : 0x00002001,
    INST_QUERY_PIC_BY_FV        : 0x00002002,
    INST_QUERY_SUB_LIB          : 0x00002010,
    INST_QUERY_SUB_LIB_IMPORT   : 0x00002011,
    INST_QUERY_SUB_MAN          : 0x00002020,
    INST_QUERY_SUB_MAN_PIC      : 0x00002021,
    INST_QUERY_DEV              : 0x00002030,
    
    INST_APPLY_SUB_LIB_CHECK    : 0x00003000,
    INST_APPLY_SUB_LIB_IMPORT   : 0x00003001,
    INST_APPLY_DEV_IMPORT       : 0x00003002,
};

})(jQuery);