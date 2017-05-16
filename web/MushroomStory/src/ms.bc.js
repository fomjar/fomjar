ms.bc = {};

(function() {

ms.bc.scene = {};
ms.bc.scene.current = null;
ms.bc.scene.switch = function(scene) {
    var children = Laya.stage._childs;
    if (0 < children.length) {
        for (var i = 0; i < children.length; i++) {
            if (i < children.length - 1) children[i].hide();
            else children[i].hide({done : function() {scene.setup();}});    // last
        }
    } else {scene.setup();}
    ms.bc.scene.current = scene;
};
ms.bc.scene.Scene = function() {
    var sc = {};
    sc.setup = function() {};
    return sc;
};

ms.bc.scene.scene_load = function() {
    if (!ms.bc.scene._scene_load) {
        var progress = new ms.ui.ProgressBar();
        progress.width = Laya.stage.width / 2;
        progress.pos(Laya.stage.width / 2, Laya.stage.height / 2);

        var load = function(progress) {
            progress.value = 0;
            progress.tween_to({value : 1});
            Laya.timer.once(1000, this, function() {
                ms.bc.scene.switch(ms.bc.scene.scene_launch());
            });
        };
        
        var sc = new ms.bc.scene.Scene();
        sc.setup = function() {
            progress.show();

            load(progress);
        };
        ms.bc.scene._scene_load = sc;
    }
    return ms.bc.scene._scene_load;
};

ms.bc.scene.scene_launch = function() {
    if (!ms.bc.scene._scene_launch) {
        var logo = new ms.ui.Text();
        logo.text       = '蘑菇物语\nMushroom Story';
        logo.color      = g.d.color.ui_bd;
        logo.bold       = true;
        logo.fontSize   = g.d.font.logo;
        logo.width      = Laya.stage.width;
        logo.pos(0, logo.fontSize);

        var name = new ms.ui.Input();
        name.input.prompt = '输入昵称';
        name.size(name.input.fontSize * 16, name.input.fontSize * 2);
        name.pos(Laya.stage.width / 2, Laya.stage.height - name.height * 4);

        var error = new ms.ui.Text();
        error.color = g.d.color.ui_er;
        error.bold = true;
        error.pos(name.x + name.width / 2, name.y - name.height / 2);

        var submit = new ms.ui.Button();
        submit.label = '开始游戏';
        submit.pos(Laya.stage.width / 2, name.y + name.input.fontSize * 3);
        submit.on(Laya.Event.MOUSE_UP, submit, function() {
            var text = name.input.text;
            if (0 == text.length) {
                error.text = '昵称不能为空';
                error.show({time : 2000});
                return;
            }
            ms.bc.scene.switch(ms.bc.scene.scene_play());
        });

        var sc = new ms.bc.scene.Scene();
        sc.setup = function() {
            logo.show();
            name.show();
            submit.show();
        };
        ms.bc.scene._scene_launch = sc;
    }
    return ms.bc.scene._scene_launch;
};

ms.bc.scene.scene_play = function() {
    if (!ms.bc.scene._scene_play) {
        var sc = new ms.bc.scene.Scene();
        sc.setup = function() {
            ms.bc.o.map().show();
            ms.bc.o.hero().show({parent : ms.bc.o.map()});
            ms.bc.o.icon_chytridiomycota().show({parent : ms.bc.o.map()});
        };
        ms.bc.scene._scene_play = sc;
    }
    return ms.bc.scene._scene_play;
};


ms.bc.o = {};

ms.bc.o.Object = function() {
    var o = new Laya.Sprite();
    return o;
};

ms.bc.o.Spore = function() {
    var o = new ms.bc.o.Object();
    o.radius = 0;
    o.speed = g.d.spore.speed;
    o.moving = {x : 0, y : 0};
    o.look = {x : 2<<8, y : Laya.stage.height / 2};
    o.move_l_b = function() {this.moving.x = -this.speed;};
    o.move_l_e = function() {this.moving.x = 0;};
    o.move_r_b = function() {this.moving.x = this.speed;};
    o.move_r_e = function() {this.moving.x = 0;};
    o.move_u_b = function() {this.moving.y = -this.speed;};
    o.move_u_e = function() {this.moving.y = 0;};
    o.move_d_b = function() {this.moving.y = this.speed;};
    o.move_d_e = function() {this.moving.y = 0;};
    o.auto_move = function() {Laya.timer.frameLoop(1, this, function() {this.pos(this.x + this.moving.x, this.y + this.moving.y);});};
    o.auto_move();
    o.auto_look = function() {Laya.timer.frameLoop(1, this, function() {this.rotation = Math.atan2(this.look.y - this.y, this.look.x - this.x) * 180 / Math.PI;});};
    o.auto_look();
    o.paint = function() {
        this.graphics.clear();
        this.graphics.drawCircle(this.radius, this.radius, this.radius, g.d.color.sp_bg, g.d.color.sp_bd, g.d.color.sp_lw);
        this.graphics.drawLine(this.radius - g.d.color.sp_lw / 2, this.radius - g.d.color.sp_lw / 2, this.radius * 2 - g.d.color.sp_lw, this.radius - g.d.color.sp_lw / 2, g.d.color.sp_bd, g.d.color.sp_lw);
    };
    o.watch('radius', null, function(p, o, n) {
        this.size(n * 2, n * 2);
        this.paint();
    });
    o.auto_pivot();
    o.radius = g.d.color.sp_rd;
    o.pos(Laya.stage.width / 2, Laya.stage.height / 2);
    return o;
};

ms.bc.o.map = function() {
    if (!ms.bc.o._map) {
        ms.bc.o._map = new ms.bc.o.Object();
    }
    return ms.bc.o._map;
};
ms.bc.o.hero = function() {
    if (!ms.bc.o._hero) {
        ms.bc.o._hero = new ms.bc.o.Spore();
        Laya.stage.on(Laya.Event.KEY_DOWN, ms.bc.o._hero, function(e) {
            switch (e.nativeEvent.key) {
            case 'w': this.move_u_b(); break;
            case 's': this.move_d_b(); break;
            case 'a': this.move_l_b(); break;
            case 'd': this.move_r_b(); break;
            }
        });
        Laya.stage.on(Laya.Event.KEY_UP, ms.bc.o._hero, function(e) {
            switch (e.nativeEvent.key) {
            case 'w': this.move_u_e(); break;
            case 's': this.move_d_e(); break;
            case 'a': this.move_l_e(); break;
            case 'd': this.move_r_e(); break;
            }
        });
        Laya.stage.on(Laya.Event.MOUSE_MOVE, ms.bc.o._hero, function(e) {
            this.look.x = e.stageX;
            this.look.y = e.stageY;
        });
    }
    return ms.bc.o._hero;
};

ms.bc.o.Icon = function() {
    var o = new ms.bc.o.Object();
    o.size_l = function() {
        o.size(g.d.size.icon_l, g.d.size.icon_l);
        o.paint();
    };
    o.size_m = function() {
        o.size(g.d.size.icon_m, g.d.size.icon_m);
        o.paint();
    };
    o.size_s = function() {
        o.size(g.d.size.icon_s, g.d.size.icon_s);
        o.paint();
    };
    o.auto_alpha();
    return o;
};
ms.bc.o.icon_chytridiomycota = function() {
    var o = new ms.bc.o.Icon();
    o.paint = function() {
        this.graphics.clear();
        var s = this.width;
        this.graphics.drawRoundRect(0, 0, s, s, g.d.color.ui_rr, '#333333', '#333333', 0);
        this.graphics.drawPath(0, 0, [
                ["moveTo", s * 2 / 5,   s * 1 / 6],
                ["lineTo", s * 3 / 5,   s * 1 / 6],
                ["lineTo", s * 4 / 5,   s * 2 / 6],
                ["lineTo", s * 1 / 5,   s * 2 / 6],
                ["closePath"]
            ],
            {fillStyle : '#666699'},
            {strokeStyle : '#666699', lineWidth : 0});
    };
    o.size_m();
    o.pos(0, Laya.stage.height - o.height);
    return o;
};

})();