// stand-ins for the LuCI client modules a view needs to build its form: the form classes only record what the view declares,
// and uci, rpc and fs read through hostUbus, which lets through only calls that read

var anything = new Proxy(function () {}, {
    get: function (target, key) {
        if (key === 'then' || key === Symbol.toPrimitive) return undefined;
        if (key === 'length') return 0;
        if (key === 'toString' || key === 'valueOf') return function () { return ''; };
        return anything;
    },
    apply: function () { return anything; },
    construct: function () { return anything; }
});

// a view leaves some promises unwatched and the host takes an unhandled rejection for an error of the whole run,
// so a promise a then makes is watched from the start
var plainThen = Promise.prototype.then;
Promise.prototype.then = function (resolved, rejected) {
    var p = plainThen.call(this, resolved, rejected);
    plainThen.call(p, undefined, function () {});
    return p;
};

function fallback(base) {
    return new Proxy(base, {
        get: function (target, key) { return key in target ? target[key] : key === 'then' ? undefined : anything; }
    });
}

function makeClass(base, props, name) {
    var ctor = function () {
        if (this.__init__) this.__init__.apply(this, arguments);
    };
    ctor.prototype = Object.create(base ? base.prototype : Object.prototype);
    ctor.prototype.constructor = ctor;
    ctor.__kind = name || (base && base.__kind) || 'Class';
    ctor.__base = base;
    for (var key in props) ctor.prototype[key] = props[key];
    ctor.extend = function (p) { return makeClass(ctor, p); };
    ctor.singleton = function () { return new ctor(); };
    ctor.instantiate = function (args) { var o = Object.create(ctor.prototype); ctor.apply(o, args); return o; };
    ctor.isSubclass = function (c) { return c && c.prototype instanceof ctor; };
    ctor.prototype.super = function (method, args) {
        var proto = base ? base.prototype : null;
        return proto && proto[method] ? proto[method].apply(this, args || []) : undefined;
    };
    ctor.prototype.varargs = function (args, offset) { return Array.prototype.slice.call(args, offset || 0); };
    return ctor;
}

var baseclass = makeClass(null, {}, 'baseclass');

var maps = [];

var CBIMap = makeClass(null, {
    __init__: function (config, title, description) {
        this.config = config;
        this.title = title;
        this.description = description;
        this.children = [];
        maps.push(this);
    },
    section: function (cls) {
        var s = cls.instantiate([this].concat(Array.prototype.slice.call(arguments, 1)));
        this.children.push(s);
        return s;
    },
    render: function () { return Promise.resolve(this); },
    load: function () { return Promise.resolve(); },
    save: function () { return Promise.resolve(); },
    lookupOption: function () { return null; },
    findElement: function () { return null; },
    chain: function () {}
}, 'Map');

var CBIJSONMap = CBIMap.extend({
    __init__: function (data, title, description) {
        CBIMap.prototype.__init__.call(this, 'json', title, description);
        this.data = data;
    }
});
CBIJSONMap.__kind = 'JSONMap';

var CBISection = makeClass(null, {
    __init__: function (map, sectiontype, title, description) {
        this.map = map;
        this.sectiontype = sectiontype;
        this.title = title;
        this.description = description;
        this.children = [];
        this.tabs = [];
    },
    tab: function (name, title, description) {
        var t = { name: name, title: title, description: description, children: [] };
        this.tabs.push(t);
        this.tabs[name] = t;
    },
    getOption: function (name) { return this.children.filter(function (o) { return o.option === name; })[0] || null; },
    option: function (cls, name, title, description) {
        var o = cls.instantiate([this.map, this, name].concat(Array.prototype.slice.call(arguments, 2)));
        this.children.push(o);
        return o;
    },
    taboption: function (tab) {
        var o = this.option.apply(this, Array.prototype.slice.call(arguments, 1));
        o.tab = tab;
        return o;
    },
    cfgsections: function () { return []; },
    cfgvalue: function () { return null; },
    filter: function () { return true; },
    load: function () { return Promise.resolve(); },
    render: function () { return anything; }
}, 'TypedSection');

var CBINamedSection = CBISection.extend({
    __init__: function (map, section, sectiontype, title, description) {
        CBISection.prototype.__init__.call(this, map, sectiontype, title, description);
        this.section = section;
    }
});
CBINamedSection.__kind = 'NamedSection';
var CBITableSection = CBISection.extend({});
CBITableSection.__kind = 'TableSection';
var CBIGridSection = CBITableSection.extend({});
CBIGridSection.__kind = 'GridSection';

var CBIOption = makeClass(null, {
    __init__: function (map, section, option, title, description) {
        this.map = map;
        this.section = section;
        this.option = option;
        this.title = title;
        this.description = description;
        this.deps = [];
        this.keylist = [];
        this.vallist = [];
    },
    depends: function (field, value) {
        var d = (typeof field === 'object') ? field : {};
        if (typeof field !== 'object') d[field] = value;
        this.deps.push(d);
    },
    value: function (key, label) {
        this.keylist.push(String(key));
        this.vallist.push(label === undefined ? String(key) : (typeof label === 'string' ? label : String(key)));
    },
    cfgvalue: function () { return null; },
    formvalue: function () { return null; },
    textvalue: function () { return ''; },
    write: function () {},
    remove: function () {},
    load: function () { return Promise.resolve(); },
    getUIElement: function () { return null; },
    isValid: function () { return true; },
    isActive: function () { return true; },
    render: function () { return anything; }
}, 'Value');

function optionClass(kind) {
    var c = CBIOption.extend({});
    c.__kind = kind;
    return c;
}

var CBISectionValue = CBIOption.extend({
    __init__: function (map, section, option, cbiClass) {
        CBIOption.prototype.__init__.call(this, map, section, option);
        this.subsection = cbiClass.instantiate([map].concat(Array.prototype.slice.call(arguments, 4)));
    }
});
CBISectionValue.__kind = 'SectionValue';

var form = {
    Map: CBIMap,
    JSONMap: CBIJSONMap,
    TypedSection: CBISection,
    NamedSection: CBINamedSection,
    TableSection: CBITableSection,
    GridSection: CBIGridSection,
    AbstractValue: CBIOption,
    Value: CBIOption,
    SectionValue: CBISectionValue
};
['DynamicList', 'ListValue', 'RichListValue', 'RangeSliderValue', 'MultiValue', 'TextValue', 'DummyValue', 'Button',
    'HiddenValue', 'FileUpload', 'DirectoryPicker'].forEach(function (k) { form[k] = optionClass(k); });
form.Flag = CBIOption.extend({
    __init__: function () {
        CBIOption.prototype.__init__.apply(this, arguments);
        this.enabled = '1';
        this.disabled = '0';
        this.default = this.disabled;
    }
});
form.Flag.__kind = 'Flag';
form = fallback(form);

var widgets = fallback({});
['ZoneSelect', 'ZoneForwards', 'NetworkSelect', 'DeviceSelect', 'IPSelect', 'UserSelect', 'GroupSelect', 'CBIZoneSelect']
    .forEach(function (k) { widgets[k] = optionClass('widgets.' + k); });

function ubus(object, method, args) {
    if (typeof hostUbus !== 'function') return null;
    var r = hostUbus(object, method, JSON.stringify(args || {}));
    return r == null ? null : JSON.parse(r);
}

var uciCache = {};

function uciSections(conf) {
    var values = uciCache[conf] || {};
    return Object.keys(values).map(function (k) { return values[k]; }).sort(function (a, b) { return a['.index'] - b['.index']; });
}

function uciResolve(conf, sid) {
    var m = /^@([^\[]+)\[(-?\d+)\]$/.exec(sid || '');
    if (!m) return sid;
    var list = uciSections(conf).filter(function (s) { return s['.type'] === m[1]; });
    var i = +m[2];
    var s = list[i < 0 ? list.length + i : i];
    return s ? s['.name'] : undefined;
}

var uci = fallback({
    load: function (packages) {
        (Array.isArray(packages) ? packages : [packages]).forEach(function (conf) {
            if (!(conf in uciCache)) {
                var r = ubus('uci', 'get', { config: conf });
                uciCache[conf] = (r && r.values) || {};
            }
        });
        return Promise.resolve(Array.isArray(packages) ? packages : [packages]);
    },
    unload: function () {},
    get: function (conf, sid, opt) {
        var s = (uciCache[conf] || {})[uciResolve(conf, sid)];
        if (!s) return undefined;
        return opt == null ? s : s[opt];
    },
    get_first: function (conf, type, opt) {
        var s = uciSections(conf).filter(function (x) { return type == null || x['.type'] === type; })[0];
        if (!s) return undefined;
        return opt == null ? s : s[opt];
    },
    get_bool: function (conf, sid, opt) {
        var v = this.get(conf, sid, opt);
        return ['1', 'on', 'true', 'yes', 'enabled'].indexOf(String(v)) >= 0;
    },
    sections: function (conf, type, cb) {
        var list = uciSections(conf).filter(function (s) { return type == null || s['.type'] === type; });
        if (cb) list.forEach(function (s) { cb.call(this, s, s['.name']); });
        return list;
    },
    resolveSID: function (conf, sid) { return uciResolve(conf, sid); },
    set: function () {}, unset: function () {}, add: function () { return 'cfg000000'; }, remove: function () {},
    changes: function () { return Promise.resolve({}); },
    save: function () { return Promise.resolve(); },
    apply: function () { return Promise.resolve(); }
});

function expectValue(expect, data) {
    var keys = expect ? Object.keys(expect) : [];
    if (!keys.length) return data;
    var key = keys[0], def = JSON.parse(JSON.stringify(expect[key]));
    var value = key === '' ? data : (data && typeof data === 'object' ? data[key] : undefined);
    if (value === undefined || value === null) return def;
    if (Array.isArray(def) && !Array.isArray(value)) return def;
    return value;
}

var rpc = fallback({
    declare: function (options) {
        return function () {
            var args = {}, params = options.params || [];
            for (var i = 0; i < params.length; i++) args[params[i]] = arguments[i];
            var data = ubus(options.object, options.method, args);
            var value = expectValue(options.expect, data);
            if (typeof options.filter === 'function') value = options.filter(value, args);
            return Promise.resolve(value);
        };
    },
    list: function () { return Promise.resolve({}); }
});

var fs = fallback({
    exec: function () { return Promise.resolve({ code: 0, stdout: '', stderr: '' }); },
    exec_direct: function () { return Promise.resolve(''); },
    read: function (path) { var r = ubus('file', 'read', { path: path }); return r && r.data != null ? Promise.resolve(r.data) : Promise.reject(new Error('NotFoundError')); },
    read_direct: function (path, type) {
        var r = ubus('file', 'read', { path: path });
        var text = (r && r.data) || '';
        if (type === 'json') { try { return Promise.resolve(JSON.parse(text)); } catch (e) { return Promise.resolve(null); } }
        return Promise.resolve(text);
    },
    lines: function (path) { var r = ubus('file', 'read', { path: path }); return Promise.resolve(((r && r.data) || '').split(/\n/).filter(Boolean)); },
    list: function (path) { var r = ubus('file', 'list', { path: path }); return Promise.resolve((r && r.entries) || []); },
    stat: function (path) { var r = ubus('file', 'stat', { path: path }); return r ? Promise.resolve(r) : Promise.reject(new Error('NotFoundError')); },
    write: function () { return Promise.resolve(); },
    remove: function () { return Promise.resolve(); }
});

String.prototype.format = function () {
    var args = arguments, i = 0;
    return this.replace(/%([-+ 0#]*)(\d*)(?:\.(\d+))?([sdfxXoqhtmcb%])/g, function (all, flags, width, precision, type) {
        if (type === '%') return '%';
        var v = args[i++];
        if (type === 'd') return String(Math.trunc(+v || 0));
        if (type === 'f') return (+v || 0).toFixed(precision ? +precision : 6);
        if (type === 'x') return (+v || 0).toString(16);
        if (type === 'X') return (+v || 0).toString(16).toUpperCase();
        return v == null ? '' : String(v);
    });
};
String.prototype.trimTrailing = function (s) { return this.endsWith(s) ? this.slice(0, -s.length) : String(this); };
String.format = function (format) { return String.prototype.format.apply(String(format), Array.prototype.slice.call(arguments, 1)); };

var hints = fallback({
    getMACHints: function () { return []; }, getIPAddrHints: function () { return []; }, getIP6AddrHints: function () { return []; },
    getHostnameByMACAddr: function () { return null; }, getIPAddrByMACAddr: function () { return null; },
    getMACAddrByIPAddr: function () { return null; }, getHostnameByIPAddr: function () { return null; }
});
var network = fallback({
    getDevices: function () { return Promise.resolve([]); },
    getNetworks: function () { return Promise.resolve([]); },
    getWANNetworks: function () { return Promise.resolve([]); },
    getWAN6Networks: function () { return Promise.resolve([]); },
    getWifiDevices: function () {
        uci.load('wireless');
        return Promise.resolve(uci.sections('wireless', 'wifi-device').map(function (s) {
            return fallback({
                sid: s['.name'], getName: function () { return s['.name']; }, get: function (opt) { return s[opt]; },
                getWifiNetworks: function () {
                    return Promise.resolve(uci.sections('wireless', 'wifi-iface').filter(function (n) { return n.device === s['.name']; }).map(wifiNetwork));
                }
            });
        }));
    },
    getWifiNetworks: function () { return Promise.resolve([]); },
    getWifiNetwork: function (name) {
        uci.load('wireless');
        var s = uci.get('wireless', name);
        return Promise.resolve(s && s['.type'] === 'wifi-iface' ? wifiNetwork(s) : null);
    },
    getSwitchTopologies: function () { return Promise.resolve({}); },
    getHostHints: function () { return Promise.resolve(hints); },
    getProtocols: function () {
        var names = Object.keys(ubus('network', 'get_proto_handlers') || {});
        if (names.indexOf('none') < 0) names.push('none');
        return names.sort().map(function (name) {
            var own = requireModule('protocol.' + name);
            return fallback({
                getProtocol: function () { return name; },
                getI18n: function () { return 'getI18n' in own ? own.getI18n() : name; },
                isVirtual: function () { return 'isVirtual' in own && own.isVirtual() === true; }
            });
        });
    },
    getNetwork: function (name) {
        uci.load('network');
        var s = uci.get('network', name);
        if (!s || s['.type'] !== 'interface') return Promise.resolve(null);
        return Promise.resolve(fallback({
            getName: function () { return name; },
            getProtocol: function () { return s.proto; },
            get: function (opt) { return s[opt]; },
            getL3Device: function () { return null; },
            renderFormOptions: function () { return Promise.resolve(); }
        }));
    },
    getDevice: function () { return Promise.resolve(null); },
    flushCache: function () { return Promise.resolve(); },
    registerProtocol: function (name, methods) { return fallback(methods); }
});

function wifiNetwork(s) {
    return fallback({
        sid: s['.name'], getName: function () { return s['.name']; }, getWifiDeviceName: function () { return s.device; },
        get: function (opt) { return s[opt]; }, getI18n: function () { return s.ssid || s['.name']; },
        getIfname: function () { return null; }, getActiveBSSID: function () { return null; }
    });
}

var view = makeClass(null, {
    load: function () { return Promise.resolve(); },
    render: function () { return anything; },
    handleSave: null, handleSaveApply: null, handleReset: null
}, 'view');

var dom = anything, ui = anything, poll = anything, validation = anything, request = anything;

var modules = {
    baseclass: baseclass, view: view, form: form, uci: uci, rpc: rpc, fs: fs, network: network, dom: dom, ui: ui, poll: poll,
    validation: validation, request: request
};
var fallbacks = { 'tools.widgets': widgets };

var L = fallback({
    env: { sessionid: '00000000000000000000000000000000', requestpath: [], dispatchpath: [] },
    Class: baseclass,
    ui: ui, dom: dom, uci: uci, Poll: poll, rpc: rpc, fs: fs, network: network,
    resolveDefault: function (p, d) { return Promise.resolve(p).catch(function () { return d; }); },
    toArray: function (x) { return x == null ? [] : (Array.isArray(x) ? x : (typeof x === 'object' ? Object.keys(x).map(function (k) { return x[k]; }) : [x])); },
    isObject: function (x) { return x != null && typeof x === 'object'; },
    sortedKeys: function (o) { return Object.keys(o || {}).sort(); },
    bind: function (fn, ctx) { var args = Array.prototype.slice.call(arguments, 2); return function () { return fn.apply(ctx, args.concat(Array.prototype.slice.call(arguments))); }; },
    hasSystemFeature: function () { return true; },
    url: function () { return '/'; }, resource: function () { return '/'; }, media: function () { return '/'; },
    require: function (name) { return Promise.resolve(modules[name] || anything); },
    error: function (type, msg) { throw new Error((type || 'Error') + ': ' + msg); },
    raise: function (type, msg) { throw new Error((type || 'Error') + ': ' + msg); },
    isElement: function () { return false; },
    naturalCompare: function (a, b) { return String(a).localeCompare(String(b)); }
});

var cbi_update_table = function () {}, cbi_init = function () {};
var _ = function (s) { return s; };
var N_ = function (n, s, p) { return n == 1 ? s : p; };
var E = function () { return anything; };
var window = fallback({ L: L, location: { hash: '', href: '/', pathname: '/' }, setTimeout: function () { return 0; }, clearTimeout: function () {} });
var document = anything;
var setTimeout = window.setTimeout, clearTimeout = window.clearTimeout, setInterval = window.setTimeout, clearInterval = window.clearTimeout;
var console = { log: function () {}, warn: function () {}, error: function () {}, debug: function () {} };

function loadView(source) {
    var names = [], values = [], re = /['"]require ([\w.]+)(?: as (\w+))?['"]/g, m;
    while ((m = re.exec(source)) !== null) {
        names.push(m[2] || m[1].split('.').pop());
        values.push(requireModule(m[1]));
    }
    return Function.apply(null, names.concat([source])).apply(null, values);
}

function requireModule(name) {
    if (name in modules) return modules[name];
    modules[name] = fallbacks[name] || anything;
    var source = typeof hostModule === 'function' ? hostModule(name) : null;
    if (source) {
        try {
            var exported = loadView(source);
            modules[name] = (typeof exported === 'function' && exported.prototype && !exported.prototype.render) ? new exported() : exported;
        } catch (e) {
            modules[name] = fallbacks[name] || anything;
        }
    }
    return modules[name];
}

function dumpOption(o, absent) {
    var proto = Object.getPrototypeOf(o), own = [];
    var plumbing = ['constructor', 'super', 'varargs', '__init__', '__name__'];
    for (var p = proto; p && p !== CBIOption.prototype; p = Object.getPrototypeOf(p))
        Object.keys(p).forEach(function (k) { if (own.indexOf(k) < 0 && plumbing.indexOf(k) < 0) own.push(k); });
    var out = { kind: o.__name__ || o.constructor.__kind, name: o.ucioption || o.option };
    ['title', 'tab', 'datatype', 'default', 'placeholder', 'rmempty', 'optional', 'readonly', 'password', 'size', 'enabled', 'disabled', 'widget', 'multiple']
        .forEach(function (k) { if (o[k] !== undefined && typeof o[k] !== 'function' && typeof o[k] !== 'object') out[k] = o[k]; });
    if (typeof o.datatype === 'function') out.datatype = '<function>';
    if (typeof o.validate === 'function' && Object.prototype.hasOwnProperty.call(o, 'validate')) out.validate = '<function>';
    if (o.keylist.length) out.values = o.keylist.map(function (k, i) { return [k === absent[out.name] ? '' : k, o.vallist[i]]; });
    if (o.deps.length) out.depends = o.deps.map(function (d) {
        var r = {};
        Object.keys(d).forEach(function (k) { r[k] = k in absent && d[k] === absent[k] ? '' : d[k]; });
        return r;
    });
    if (own.length) out.custom = own;
    if (o.subsection) out.subsection = dumpSection(o.subsection);
    return out;
}

function dumpSection(s) {
    var out = { kind: s.constructor.__kind, type: s.sectiontype, title: s.title };
    if (s.section !== undefined) out.section = s.section;
    if (s.uciconfig) out.config = s.uciconfig;
    ['anonymous', 'named', 'addremove', 'sortable', 'nodescriptions', 'added'].forEach(function (k) { if (s[k] !== undefined) out[k] = s[k]; });
    if (s.tabs.length) out.tabs = s.tabs.map(function (t) { return { name: t.name, title: t.title }; });
    var absent = Object.create(null);
    s.children.forEach(function (o) {
        if (!o.keylist.length || o.default !== undefined || !Object.prototype.hasOwnProperty.call(o, 'cfgvalue')) return;
        try {
            var shown = o.cfgvalue('cfgabsent');
            if (typeof shown === 'string' && shown !== '' && o.keylist.indexOf(shown) >= 0) absent[o.ucioption || o.option] = shown;
        } catch (e) {
        }
    });
    out.options = s.children.map(function (o) { return dumpOption(o, absent); });
    return out;
}

function countOptions(sections) {
    return sections.reduce(function (n, s) {
        return n + s.options.length + s.options.reduce(function (m, o) { return m + (o.subsection ? countOptions([o.subsection]) : 0); }, 0);
    }, 0);
}

async function schemaOf(source) {
    maps.length = 0;
    var View = loadView(source);
    if (typeof View !== 'function' || !View.prototype || typeof View.prototype.render !== 'function') return JSON.stringify({ module: true });
    var v = new View();
    var data = await v.load();
    await v.render(data);
    var forms = maps.filter(function (m) { return !(m instanceof CBIJSONMap); });
    for (var m of forms) await withModal(m);
    for (var f of forms) for (var s of f.children) if (Object.prototype.hasOwnProperty.call(s, 'handleAdd')) await onAdd(s);
    var result = forms.map(function (m) { return { config: m.config, title: m.title, sections: m.children.filter(stored).map(dumpSection) }; });
    return JSON.stringify({ maps: result, options: result.reduce(function (n, m) { return n + countOptions(m.sections); }, 0) });
}

// a grid whose dialog adds fields as it opens is opened for each of its sections in turn,
// and the fields of the dialogs replace its own of the same names
async function withModal(m) {
    for (var s of m.children.slice()) {
        if (!Object.prototype.hasOwnProperty.call(s, 'addModalOptions')) continue;
        uci.load(m.config);
        await settled(function () { return s.load(); });
        var sids = listed(s);
        var fresh = sids.length ? null : added(m.config, s.sectiontype);
        if (fresh) sids = [fresh];
        var types = {};
        for (var sid of sids) {
            var type = (uci.get(m.config, sid) || {})['.type'];
            if (!type) continue;
            var modal = new CBINamedSection(m, sid, type);
            modal.tabs = tabsOf(s.tabs);
            await settled(function () { return s.addModalOptions(modal, sid); });
            var t = types[type] = types[type] || { found: {}, names: [], opened: 0, tabs: modal.tabs };
            t.opened++;
            modal.tabs.forEach(function (tab) {
                if (!t.tabs[tab.name]) {
                    t.tabs.push(tab);
                    t.tabs[tab.name] = tab;
                }
            });
            var tie = tieOf(modal, sid);
            modal.children.forEach(function (o) {
                var name = keyOf(o), f = t.found[name];
                if (!f) {
                    t.found[name] = f = { option: o, sids: {}, deps: [], always: false, tied: null };
                    t.names.push(name);
                } else if (f.option.title !== o.title) {
                    f.option.title = undefined;
                }
                f.sids[sid] = true;
                if (!o.deps.length) f.always = true;
                var tied = Object.keys(tie).filter(function (k) {
                    return o.deps.every(function (d) { return k in d && d[k] === tie[k]; });
                });
                f.tied = f.tied ? f.tied.filter(function (k) { return tied.indexOf(k) >= 0; }) : tied;
                o.deps.forEach(function (d) {
                    if (!f.deps.some(function (e) { return JSON.stringify(e) === JSON.stringify(d); })) f.deps.push(d);
                });
            });
        }
        Object.keys(types).forEach(function (type) {
            var t = types[type];
            var fields = t.names.map(function (name) {
                var f = t.found[name];
                f.option.deps = f.always ? [] : Object.keys(f.sids).length < t.opened ? f.deps : untied(f.deps, f.tied);
                return f.option;
            });
            if (type === s.sectiontype) {
                s.children.forEach(function (o) {
                    var f = t.found[keyOf(o)];
                    if (!f || o.default === undefined || f.option.default === undefined || String(o.default) === String(f.option.default)) return;
                    s.added = s.added || {};
                    s.added[keyOf(o)] = String(f.option.default);
                    f.option.default = o.default;
                });
                s.children = s.children.filter(function (o) { return t.names.indexOf(keyOf(o)) < 0; }).concat(fields);
                t.tabs.forEach(function (tab) {
                    if (!s.tabs[tab.name]) {
                        s.tabs.push(tab);
                        s.tabs[tab.name] = tab;
                    }
                });
            } else {
                var other = m.section(CBISection, type);
                other.addremove = true;
                other.tabs = t.tabs;
                other.children = fields;
            }
        });
        if (fresh) delete uciCache[m.config][fresh];
    }
}

async function onAdd(s) {
    var before = maps.length, sid = null, type = null, values = {};
    var add = uci.add, set = uci.set;
    uci.add = function (conf, sectiontype, name) {
        type = sectiontype;
        sid = name || 'cfgadded';
        return sid;
    };
    uci.set = function (conf, section, option, value) {
        if (section === sid && option != null && value != null && String(value) !== '') values[option] = Array.isArray(value) ? value.join('\n') : String(value);
    };
    try {
        await settled(function () { return s.handleAdd(anything); });
    } finally {
        uci.add = add;
        uci.set = set;
    }
    var target = type === s.sectiontype ? s : s.map.children.filter(function (x) { return x.sectiontype === type; })[0];
    if (target && Object.keys(values).length) target.added = Object.assign(target.added || {}, values);
    s.named = maps.slice(before).some(function (m) {
        return m.children.some(function (x) { return x.section === '_new_' && x.children.some(function (o) { return o.option === 'name'; }); });
    }) || undefined;
}

function keyOf(o) {
    return o.subsection ? o.option + ':' + o.subsection.sectiontype : o.ucioption || o.option;
}

function listed(s) {
    var own = [];
    if (Object.prototype.hasOwnProperty.call(s, 'cfgsections')) {
        try {
            own = s.cfgsections() || [];
        } catch (e) {
            own = [];
        }
    }
    return own.length ? own : uci.sections(s.map.config, s.sectiontype).map(function (x) { return x['.name']; });
}

function added(config, type) {
    uci.load(config);
    var values = uciCache[config];
    var name = 'cfg' + type.replace(/[^a-z0-9]/gi, '') + 'new';
    values[name] = { '.name': name, '.type': type, '.anonymous': true, '.index': Object.keys(values).length };
    return name;
}

function tabsOf(tabs) {
    var copy = tabs.slice();
    copy.forEach(function (t) { copy[t.name] = t; });
    return copy;
}

// LuCI hides the fields once a value they are tied to changes, until the section is saved and the dialog opens again
// with the fields of the new ones: the protocol of an interface, the provider of a DDNS service
function tieOf(modal, sid) {
    var tie = {};
    modal.children.filter(function (o) { return o.option === '_switch_proto'; }).forEach(function (o) {
        o.deps.forEach(function (d) {
            Object.keys(d).forEach(function (k) {
                if (k.charAt(0) === '!' || k in tie) return;
                var field = modal.children.filter(function (x) { return (x.ucioption || x.option) === k && Object.prototype.hasOwnProperty.call(x, 'cfgvalue'); })[0];
                try {
                    tie[k] = field ? field.cfgvalue(sid) : uci.get(modal.map.config, sid, k);
                } catch (e) {
                    tie[k] = uci.get(modal.map.config, sid, k);
                }
            });
        });
    });
    return tie;
}

function untied(deps, keys) {
    var rest = [];
    deps.forEach(function (d) {
        var r = {};
        Object.keys(d).forEach(function (k) { if (keys.indexOf(k) < 0) r[k] = d[k]; });
        if (!rest.some(function (e) { return JSON.stringify(e) === JSON.stringify(r); })) rest.push(r);
    });
    return rest.some(function (d) { return !Object.keys(d).length; }) ? [] : rest;
}

// a stand-in never answers what waits for the page, so a call is given up where it fails or after the work it can do at once
function settled(call) {
    var left = 10000;
    var turns = new Promise(function (resolve) {
        (function tick() { if (--left > 0) Promise.resolve().then(tick); else resolve(); })();
    });
    try {
        return Promise.race([Promise.resolve(call()).catch(function () {}), turns]);
    } catch (e) {
        return Promise.resolve();
    }
}

// not a placeholder for a table of state (_active_rules of UPnP), nor one that picks its rows itself from a type the config
// does not have and cannot add to (the plugins of statistics)
function stored(s) {
    if (s.children.some(function (o) { return o.subsection; })) return true;
    if (String(s.sectiontype).charAt(0) === '_') return false;
    if (!Object.prototype.hasOwnProperty.call(s, 'cfgsections') || s.addremove) return true;
    uci.load(s.map.config);
    return uci.sections(s.map.config, s.sectiontype).length > 0;
}

async function protocolOf(source) {
    maps.length = 0;
    var protocol = loadView(source);
    var s = new CBIMap('network').section(CBISection, 'interface');
    await protocol.renderFormOptions(s);
    return JSON.stringify({ maps: [{ config: 'network', sections: [dumpSection(s)] }] });
}

// the host reads the outcome from globals: evaluate() hands back a pending Promise as is. The outcome is ASCII, with the
// rest escaped as JSON escapes it: the bridge breaks a character outside the basic plane, as the mark of the custom
// provider of DDNS, and cuts the text after it short
var schemaResult = null, schemaError = null;
function ascii(text) {
    return text.replace(/[^\x00-\x7f]/g, function (c) { return '\\u' + ('000' + c.charCodeAt(0).toString(16)).slice(-4); });
}
function runSchema(source) {
    schemaOf(source).then(
        function (r) { schemaResult = ascii(r); },
        function (e) { schemaError = ascii((e && e.message) + '\n' + (e && e.stack)); });
}
function runProtocol(source) {
    protocolOf(source).then(
        function (r) { schemaResult = ascii(r); },
        function (e) { schemaError = ascii((e && e.message) + '\n' + (e && e.stack)); });
}
