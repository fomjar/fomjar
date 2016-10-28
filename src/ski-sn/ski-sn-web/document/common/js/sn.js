
var sn = {};
sn.geo = {};

(function($) {

fomjar.framework.phase.append('ini', init_event);
fomjar.framework.phase.append('dom', build_frame);

function build_frame() {
    var sn = $('<div></div>');
    sn.addClass('sn');
    var bg = $("<div><img src='/bg.jpg'/></div>");
    bg.addClass('bg');
    var head = $('<div></div>');
    head.addClass('head');
    var body = $('<div></div>');
    body.addClass('body');
    var foot = $('<div></div>');
    foot.addClass('foot');
    
    sn.append([bg, head, body, foot]);
    $('body').append(sn);
}

function init_event() {
    $('body').bind('touchstart', function() {});
}

})(jQuery);
