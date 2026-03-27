export namespace gui {
	
	export class DeviceInfo {
	    name: string;
	    ip: string;
	
	    static createFrom(source: any = {}) {
	        return new DeviceInfo(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.name = source["name"];
	        this.ip = source["ip"];
	    }
	}
	export class FileInfo {
	    name: string;
	    path: string;
	    size: number;
	    is_dir: boolean;
	
	    static createFrom(source: any = {}) {
	        return new FileInfo(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.name = source["name"];
	        this.path = source["path"];
	        this.size = source["size"];
	        this.is_dir = source["is_dir"];
	    }
	}
	export class HistoryEntry {
	    id: string;
	    file_name: string;
	    file_size: number;
	    direction: string;
	    peer_name: string;
	    status: string;
	    error?: string;
	    timestamp: string;
	
	    static createFrom(source: any = {}) {
	        return new HistoryEntry(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.id = source["id"];
	        this.file_name = source["file_name"];
	        this.file_size = source["file_size"];
	        this.direction = source["direction"];
	        this.peer_name = source["peer_name"];
	        this.status = source["status"];
	        this.error = source["error"];
	        this.timestamp = source["timestamp"];
	    }
	}
	export class PeerInfo {
	    name: string;
	    address: string;
	    port: number;
	    ip: string;
	
	    static createFrom(source: any = {}) {
	        return new PeerInfo(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.name = source["name"];
	        this.address = source["address"];
	        this.port = source["port"];
	        this.ip = source["ip"];
	    }
	}
	export class Settings {
	    download_dir: string;
	    auto_accept: boolean;
	    port: number;
	    device_name: string;
	
	    static createFrom(source: any = {}) {
	        return new Settings(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.download_dir = source["download_dir"];
	        this.auto_accept = source["auto_accept"];
	        this.port = source["port"];
	        this.device_name = source["device_name"];
	    }
	}

}

