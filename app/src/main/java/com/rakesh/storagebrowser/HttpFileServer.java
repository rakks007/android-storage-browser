package com.rakesh.storagebrowser;

import android.content.Context;
import android.os.Environment;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

public class HttpFileServer {
    private static final long IDLE_TIMEOUT_NANOS=TimeUnit.MINUTES.toNanos(10);
    private static final int PAGE_SIZE=100;
    private final Context context; private final List<StorageRoot> roots;
    private final String pin=String.format(Locale.US,"%06d",new SecureRandom().nextInt(1_000_000));
    private final Set<String> sessions=ConcurrentHashMap.newKeySet(); private volatile boolean running;
    private volatile long lastCommunicationNanos=System.nanoTime();
    private ServerSocket socket; private String lanAddress; private final ExecutorService pool=Executors.newFixedThreadPool(6);
    public HttpFileServer(Context c){context=c.getApplicationContext();roots=findStorageRoots();}
    public String getPin(){return pin;}
    public int getPort(){if(socket==null||!socket.isBound())throw new IllegalStateException("Server has not started");return socket.getLocalPort();}
    public String getLanAddress(){if(lanAddress==null)throw new IllegalStateException("Server has not started");return lanAddress;}
    public boolean isIdle(){return System.nanoTime()-lastCommunicationNanos>=IDLE_TIMEOUT_NANOS;}
    private String findLanAddress(){
        try{
            Enumeration<java.net.NetworkInterface> interfaces=java.net.NetworkInterface.getNetworkInterfaces();
            String fallback=null;
            while(interfaces.hasMoreElements()){
                java.net.NetworkInterface network=interfaces.nextElement();
                if(!network.isUp()||network.isLoopback())continue;
                Enumeration<InetAddress> addresses=network.getInetAddresses();
                while(addresses.hasMoreElements()){
                    InetAddress address=addresses.nextElement();
                    if(address.isLoopbackAddress()||!(address instanceof Inet4Address))continue;
                    String name=network.getName().toLowerCase(Locale.ROOT);
                    if(name.startsWith("wlan")||name.contains("wifi"))return address.getHostAddress();
                    if(fallback==null&&!name.startsWith("tun")&&!name.startsWith("clat"))fallback=address.getHostAddress();
                }
            }
            return fallback==null?"127.0.0.1":fallback;
        }catch(Exception e){android.util.Log.e("StorageBrowser","Unable to find a LAN IPv4 address",e);return "127.0.0.1";}
    }
    public void start() throws IOException{String address=findLanAddress();if("127.0.0.1".equals(address))throw new IOException("No active LAN IPv4 address found. Connect to Wi-Fi and try again.");socket=new ServerSocket(0,50,InetAddress.getByName(address));lanAddress=socket.getInetAddress().getHostAddress();running=true;new Thread(()->{while(running)try{Socket s=socket.accept();pool.execute(()->handle(s));}catch(IOException e){if(running)android.util.Log.e("StorageBrowser","HTTP accept failed",e);}},"http-accept").start();}
    public void close(){running=false;try{if(socket!=null)socket.close();}catch(IOException ignored){}pool.shutdownNow();}
    private void handle(Socket s){try(s){s.setSoTimeout(30000);InputStream in=new BufferedInputStream(s.getInputStream());String first=readLine(in);if(first==null)return;String[] f=first.split(" ");if(f.length<2)return;String method=f[0],target=f[1];Map<String,String> h=new HashMap<>();String line;while((line=readLine(in))!=null&&!line.isEmpty()){int x=line.indexOf(':');if(x>0)h.put(line.substring(0,x).toLowerCase(Locale.ROOT),line.substring(x+1).trim());}OutputStream out=s.getOutputStream();route(method,target,h,in,out);}catch(Exception e){android.util.Log.w("StorageBrowser","HTTP request failed",e);}}
    private String readLine(InputStream in)throws IOException{ByteArrayOutputStream line=new ByteArrayOutputStream();int value;while((value=in.read())!=-1){if(value=='\n')break;if(value!='\r')line.write(value);if(line.size()>8192)throw new IOException("HTTP line too long");}if(value==-1&&line.size()==0)return null;return line.toString(StandardCharsets.ISO_8859_1.name());}
    private void route(String method,String target,Map<String,String> headers,InputStream in,OutputStream out)throws Exception{
        URI uri=new URI(target);String path=uri.getPath();Map<String,String> q=query(uri.getRawQuery());
        if(path.equals("/login")&&method.equals("POST")){int n=Integer.parseInt(headers.getOrDefault("content-length","0"));if(n<0||n>1024){html(out,400,page("Invalid login request"));return;}byte[] b=readFully(in,n);if(b.length!=n){html(out,400,page("Incomplete login request"));return;}String supplied=query(new String(b,StandardCharsets.UTF_8)).getOrDefault("pin","");if(pin.equals(supplied)){lastCommunicationNanos=System.nanoTime();String token=UUID.randomUUID().toString();sessions.add(token);redirect(out,"/browse?path=",token);}else html(out,401,page("Incorrect PIN"));return;}
        if(!authorized(headers)){html(out,200,login());return;}
        lastCommunicationNanos=System.nanoTime();
        if(path.equals("/browse")){String browsePath=q.getOrDefault("path","");if(browsePath.isEmpty()){html(out,200,storageOverview());return;}File dir=find(browsePath);if(dir==null||!dir.isDirectory()){html(out,404,page("Folder not found"));return;}html(out,200,browse(dir,browsePath,q.getOrDefault("search",""),parsePage(q.get("page"))));return;}
        if(path.equals("/download")){File file=find(q.getOrDefault("path",""));if(file==null||!file.isFile()){html(out,404,page("File not found"));return;}download(out,file);return;}
        if(path.equals("/upload")&&method.equals("POST")){upload(headers,q,in,out);return;}
        redirect(out,"/browse?path=",null);
    }
    private synchronized void upload(Map<String,String> headers,Map<String,String> query,InputStream in,OutputStream out)throws IOException{
        String current=query.getOrDefault("path","");
        File dir=find(current);
        String name=query.getOrDefault("name","");
        long length;
        try{length=Long.parseLong(headers.getOrDefault("content-length","-1"));}catch(NumberFormatException e){html(out,400,page("Invalid file size"));return;}
        if(dir==null||!dir.isDirectory()){html(out,404,page("Folder not found"));return;}
        if(name.isEmpty()||name.equals(".")||name.equals("..")||name.contains("/")||name.contains("\\")||name.indexOf('\0')>=0||(name.startsWith(".storage-browser-")&&name.endsWith(".upload"))){html(out,400,page("Invalid or reserved file name"));return;}
        if(length<0){html(out,411,page("Content length is required"));return;}
        File destination=new File(dir,name);
        StorageRoot allowedRoot=storageRootForPath(current);
        if(allowedRoot==null||!destination.getCanonicalPath().startsWith(allowedRoot.directory.getCanonicalPath()+File.separator)){html(out,400,page("Invalid file name"));return;}
        if(destination.exists()){html(out,409,page("A file with that name already exists"));return;}
        File temporary;
        try{temporary=File.createTempFile(".storage-browser-", ".upload", dir);}
        catch(IOException e){android.util.Log.e("StorageBrowser","Unable to create upload staging file",e);html(out,500,page("Could not prepare the destination folder for upload"));return;}
        try{
            try(OutputStream fileOut=new BufferedOutputStream(new FileOutputStream(temporary))){
                byte[] buffer=new byte[65536];long remaining=length;
                while(remaining>0){int count=in.read(buffer,0,(int)Math.min(buffer.length,remaining));if(count<0){html(out,400,page("Upload was incomplete"));return;}fileOut.write(buffer,0,count);remaining-=count;lastCommunicationNanos=System.nanoTime();}
            }
            if(destination.exists()||!temporary.renameTo(destination)){html(out,409,page("Could not save the file. Check that the destination is writable and does not already exist."));return;}
            html(out,201,page("Upload complete"));
        }catch(IOException e){android.util.Log.e("StorageBrowser","File upload failed",e);html(out,500,page("Could not save the uploaded file"));}
        finally{if(temporary.exists()&&!temporary.delete())android.util.Log.w("StorageBrowser","Could not remove incomplete upload "+temporary.getName());}
    }
    private boolean authorized(Map<String,String> h){String c=h.getOrDefault("cookie","");for(String part:c.split(";")){part=part.trim();if(part.startsWith("session=")&&sessions.contains(part.substring(8)))return true;}return false;}
    private List<StorageRoot> findStorageRoots(){
        LinkedHashMap<String,StorageRoot> available=new LinkedHashMap<>();
        addStorageRoot(available,"storage0","Internal storage",Environment.getExternalStorageDirectory());
        File[] appDirs=context.getExternalFilesDirs(null);
        int index=1;
        if(appDirs!=null)for(File appDir:appDirs){
            if(appDir==null)continue;
            File volume=appDir;
            for(int i=0;i<4&&volume!=null;i++)volume=volume.getParentFile();
            if(volume==null||volume.equals(Environment.getExternalStorageDirectory()))continue;
            boolean removable=Environment.isExternalStorageRemovable(volume);
            String label=removable?"SD card":"Shared storage "+index;
            addStorageRoot(available,"storage"+index,label,volume);
            index++;
        }
        return new ArrayList<>(available.values());
    }
    private void addStorageRoot(Map<String,StorageRoot> roots,String id,String label,File directory){
        try{
            String state=directory==null?null:Environment.getStorageState(directory);
            if(directory==null||!directory.isDirectory()
                ||(!Environment.MEDIA_MOUNTED.equals(state)&&!Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)))return;
            String canonical=directory.getCanonicalPath();
            for(StorageRoot existing:roots.values())if(existing.directory.getCanonicalPath().equals(canonical))return;
            roots.put(id,new StorageRoot(id,label,directory.getCanonicalFile()));
        }catch(IOException e){android.util.Log.w("StorageBrowser","Skipping unavailable storage root "+directory,e);}
    }
    private StorageRoot storageRootForPath(String path){
        if(path==null||path.isEmpty())return null;
        String id=path.split("/",2)[0];
        for(StorageRoot root:roots)if(root.id.equals(id))return root;
        return null;
    }
    private File find(String encoded){
        String decoded=URLDecoder.decode(encoded,StandardCharsets.UTF_8);
        StorageRoot root=storageRootForPath(decoded);
        if(root==null)return null;
        File current=root.directory;
        String[] parts=decoded.split("/",-1);
        for(int i=1;i<parts.length;i++){
            String part=parts[i];
            if(part.isEmpty()||part.equals(".")||part.equals(".."))return null;
            current=new File(current,part);
            if(!current.exists())return null;
        }
        try{return current.getCanonicalPath().startsWith(root.directory.getCanonicalPath()+File.separator)||current.equals(root.directory)?current:null;}
        catch(IOException e){android.util.Log.w("StorageBrowser","Unable to resolve storage path",e);return null;}
    }
    private String storageOverview(){
        StringBuilder entries=new StringBuilder();
        for(StorageRoot root:roots){
            File[] files=root.directory.listFiles();
            String detail=files==null?"Storage volume":files.length+" items";
            entries.append("<article class='item'><div class='item-info'><b>").append(escape(root.label))
                .append("</b><small>").append(detail).append("</small></div><a href='/browse?path=")
                .append(enc(root.id)).append("'>Open</a></article>");
        }
        String body=entries.length()==0?"<p class='empty'>No mounted shared storage volumes were found. Check Android storage access and reconnect any SD card.</p>":"<section class='entries'>"+entries+"</section>";
        return page("<header><p class='eyebrow'>STORAGE BROWSER</p><h1>Storage locations</h1><div class='path'>Choose a mounted shared-storage volume.</div></header>"+body);
    }
    private static final class StorageRoot {
        final String id,label;final File directory;
        StorageRoot(String id,String label,File directory){this.id=id;this.label=label;this.directory=directory;}
    }
    private int parsePage(String value){
        try{return Math.max(0,Integer.parseInt(value));}
        catch(NumberFormatException e){return 0;}
    }
    private String browse(File dir,String current,String searchTerm,int pageIndex){
        StringBuilder rows=new StringBuilder();
        File[] listed=dir.listFiles();
        if(listed==null)return page("<h1>Folder unavailable</h1><p>Android could not list this folder. Check storage access; some protected locations, including parts of Android/data and Android/obb, cannot be browsed by apps.</p>");
        File[] files=Arrays.stream(listed)
            .filter(file->!(file.getName().startsWith(".storage-browser-")&&file.getName().endsWith(".upload")))
            .filter(file->file.getName().toLowerCase(Locale.ROOT).contains(searchTerm.toLowerCase(Locale.ROOT)))
            .toArray(File[]::new);
        Arrays.sort(files,Comparator.comparing((File x)->!x.isDirectory()).thenComparing(File::getName,String.CASE_INSENSITIVE_ORDER));
        int pageCount=Math.max(1,(files.length+PAGE_SIZE-1)/PAGE_SIZE);
        int safePage=Math.min(pageIndex,pageCount-1);
        int start=safePage*PAGE_SIZE;
        int end=Math.min(start+PAGE_SIZE,files.length);
        for(int i=start;i<end;i++){
            File file=files[i];
            String name=Optional.ofNullable(file.getName()).orElse("Unnamed");
            String next=current.isEmpty()?name:current+"/"+name;
            String href=file.isDirectory()?"/browse?path=":"/download?path=";
            rows.append("<article class='item' data-name='").append(escape(name).toLowerCase(Locale.ROOT))
                .append("'><div class='item-info'><b>").append(escape(name)).append("</b><small>")
                .append(file.isDirectory()?"Folder":file.length()+" bytes")
                .append("</small></div><a class='").append(file.isDirectory()?"open-link":"download-link")
                .append("' href='").append(href).append(enc(next)).append("'")
                .append(file.isDirectory()?"":" download='"+escape(name)+"'")
                .append(">").append(file.isDirectory()?"Open":"Download").append("</a></article>");
        }
        String parent="";
        int slash=current.lastIndexOf('/');
        parent=slash>=0?current.substring(0,slash):"";
        String search="<form class='toolbar' action='/browse' method='get'><input type='hidden' name='path' value='"+escape(current)+"'><label for='file-search'>Search all items in this folder</label><input id='file-search' name='search' type='search' value='"+escape(searchTerm)+"' placeholder='Search files and folders'><button type='submit'>Search</button></form>";
        String count=searchTerm.isEmpty()?"Showing "+(files.length==0?0:start+1)+"–"+end+" of "+files.length+" items":"Showing "+(files.length==0?0:start+1)+"–"+end+" of "+files.length+" matches";
        String listing="<p class='result-summary'>"+count+"</p><section id='entries' class='entries'>"+rows+"</section>"+(files.length==0?"<p class='empty'>"+(searchTerm.isEmpty()?"This folder is empty.":"No items match your search.")+"</p>":"");
        if(pageCount>1){
            listing+="<nav class='pagination' aria-label='Folder pages'>";
            if(safePage>0)listing+="<a href='/browse?path="+enc(current)+"&amp;search="+enc(searchTerm)+"&amp;page="+(safePage-1)+"'>Previous</a>";
            listing+="<span>Page "+(safePage+1)+" of "+pageCount+"</span>";
            if(safePage+1<pageCount)listing+="<a href='/browse?path="+enc(current)+"&amp;search="+enc(searchTerm)+"&amp;page="+(safePage+1)+"'>Next</a>";
            listing+="</nav>";
        }
        String controls="<form id='upload-form' class='upload-form' data-path='"+escape(current)+"'><label for='upload-files'>Add files to this folder</label><input id='upload-files' type='file' multiple required><button type='submit'>Upload files</button><span>Existing files are never overwritten.</span></form>";
        String progress="<aside id='transfer' class='transfer' role='status' aria-live='polite' hidden><div class='transfer-heading'><strong id='transfer-title'>Transfer</strong><span id='transfer-value'></span></div><progress id='transfer-progress' max='100' value='0'></progress></aside>";
        String script="<script>(function(){const box=document.getElementById('transfer'),title=document.getElementById('transfer-title'),value=document.getElementById('transfer-value'),bar=document.getElementById('transfer-progress');function show(label,loaded,total){box.hidden=false;title.textContent=label;bar.removeAttribute('value');if(total>0){bar.value=Math.min(100,Math.round(loaded*100/total));value.textContent=bar.value+'%';}else value.textContent='';}function hide(){box.hidden=true;}document.querySelectorAll('.download-link').forEach(link=>link.addEventListener('click',event=>{event.preventDefault();const xhr=new XMLHttpRequest();xhr.open('GET',link.href);xhr.responseType='blob';xhr.onprogress=e=>show('Downloading '+link.download,e.loaded,e.lengthComputable?e.total:0);xhr.onload=()=>{if(xhr.status!==200){hide();alert('Download failed. Please try again.');return;}const url=URL.createObjectURL(xhr.response),a=document.createElement('a');a.href=url;a.download=link.download;document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),60000);hide();};xhr.onerror=()=>{hide();alert('Download failed. Check the connection and try again.');};show('Downloading '+link.download,0,0);xhr.send();}));const form=document.getElementById('upload-form'),input=document.getElementById('upload-files');form.addEventListener('submit',async event=>{event.preventDefault();const files=[...input.files];if(!files.length)return;const base='/upload?path='+encodeURIComponent(form.dataset.path)+'&name=';let completed=0,total=files.reduce((sum,file)=>sum+file.size,0);for(const file of files){try{await new Promise((resolve,reject)=>{const xhr=new XMLHttpRequest();xhr.open('POST',base+encodeURIComponent(file.name));xhr.setRequestHeader('Content-Type','application/octet-stream');xhr.upload.onprogress=e=>show('Uploading '+file.name,completed+(e.lengthComputable?e.loaded:0),total);xhr.onload=()=>xhr.status===201?resolve():reject(new Error(xhr.status===409?'A file named '+file.name+' already exists.':'Upload failed for '+file.name+'.'));xhr.onerror=()=>reject(new Error('Upload failed. Check the connection and try again.'));show('Uploading '+file.name,completed,total);xhr.send(file);});completed+=file.size;}catch(error){hide();alert(error.message);location.reload();return;}}show('Upload complete',total,total);setTimeout(()=>location.reload(),600);});})();</script>";
        StorageRoot location=storageRootForPath(current);
        String relative=current.length()>location.id.length()?current.substring(location.id.length()+1):"";
        String displayPath=location.label+(relative.isEmpty()?"":"/"+relative);
        return page("<header><a class='parent' href='/browse?path="+enc(parent)+"'>← Parent folder</a><p class='eyebrow'>STORAGE BROWSER</p><h1>"+escape(location.label)+"</h1><div class='path'>/"+escape(displayPath)+"</div></header>"+controls+search+listing+progress+script);
    }
    private String login(){return page("<h1>Android Storage Browser</h1><p>Enter the six-digit PIN shown in the app.</p><form method='post' action='/login'><input name='pin' type='password' inputmode='numeric' maxlength='6' required autofocus><button>Connect</button></form><p class='warn'>This connection is not encrypted. Use only on a trusted Wi-Fi network.</p>");}
    private String page(String body){
        return "<!doctype html><html lang='en'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><meta name='color-scheme' content='light dark'><title>Storage Browser</title><style>"
            +":root{color-scheme:light dark;--bg:#f5f5f7;--surface:#fff;--text:#202124;--muted:#686b73;--line:#e4e4e8;--brand:#ff6900}*{box-sizing:border-box}"
            +"body{margin:0;min-height:100vh;background:var(--bg);color:var(--text);font:16px/1.5 system-ui,-apple-system,'Segoe UI',sans-serif}"
            +"main{width:100%;min-height:100vh;margin:0;padding:clamp(18px,3vw,48px)}header{margin-bottom:24px}"
            +".eyebrow{margin:26px 0 4px;color:var(--brand);font-size:12px;font-weight:800;letter-spacing:.12em}"
            +"h1{margin:0;font-size:clamp(28px,4vw,44px);line-height:1.12}.path{margin-top:10px;color:var(--muted);overflow-wrap:anywhere}"
            +".parent{display:inline-flex;align-items:center;color:var(--brand);text-decoration:none;font-weight:700}"
            +".toolbar{display:grid;grid-template-columns:1fr auto;gap:7px 16px;margin:20px 0 14px}.toolbar label,.upload-form label{grid-column:1/-1;color:var(--muted);font-size:13px;font-weight:700}"
            +".toolbar input,.upload-form input{width:100%;min-width:0;min-height:48px;margin:0;padding:10px 14px;border:1px solid var(--line);border-radius:12px;background:var(--surface);color:var(--text);font:inherit}"
            +".upload-form{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:10px 16px;margin:20px 0;padding:18px;border:1px solid var(--line);border-radius:16px;background:var(--surface)}.upload-form input{grid-column:1}.upload-form button{grid-column:2;grid-row:2}.upload-form span{grid-column:1/-1;color:var(--muted);font-size:13px}"
            +"#result-count{align-self:center;color:var(--muted);white-space:nowrap}.entries{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,320px),1fr));gap:12px}"
            +".item{display:flex;min-width:0;align-items:center;justify-content:space-between;gap:16px;padding:16px 18px;border:1px solid var(--line);border-radius:16px;background:var(--surface)}.item-info{min-width:0}.item b{display:block;overflow-wrap:anywhere}.item small{display:block;margin-top:3px;color:var(--muted)}"
            +".item a,button{flex:none;display:inline-flex;align-items:center;justify-content:center;min-height:42px;padding:9px 16px;border:0;border-radius:12px;background:var(--brand);color:#fff;text-decoration:none;font:inherit;font-weight:700}"
            +".result-summary{color:var(--muted);font-size:14px}.pagination{display:flex;justify-content:center;align-items:center;gap:20px;margin:22px 0}.pagination a{color:var(--brand);font-weight:700;text-decoration:none}"
            +".empty{padding:24px;border:1px dashed var(--line);border-radius:16px;color:var(--muted);text-align:center}.transfer{position:fixed;z-index:5;right:20px;bottom:20px;width:min(380px,calc(100vw - 40px));padding:16px 18px;border:1px solid var(--line);border-radius:16px;background:var(--surface);box-shadow:0 10px 36px #0003}.transfer-heading{display:flex;justify-content:space-between;gap:12px;margin-bottom:9px}.transfer progress{width:100%;height:9px;accent-color:var(--brand)}"
            +"input:focus-visible,a:focus-visible,button:focus-visible{outline:3px solid var(--brand);outline-offset:2px}@media(min-width:1100px){.entries{grid-template-columns:repeat(auto-fit,minmax(360px,1fr))}}"
            +"@media(max-width:520px){.upload-form{grid-template-columns:1fr}.upload-form input,.upload-form button,.upload-form span{grid-column:1;grid-row:auto}}"
            +"@media(prefers-color-scheme:dark){:root{--bg:#111214;--surface:#1c1d20;--text:#f3f3f4;--muted:#aaabb0;--line:#343539;--brand:#ff8a3d}}</style></head><body><main>"+body+"</main></body></html>";
    }
    private void html(OutputStream out,int status,String body)throws IOException{byte[] b=body.getBytes(StandardCharsets.UTF_8);String reason=switch(status){case 201->"Created";case 400->"Bad Request";case 401->"Unauthorized";case 404->"Not Found";case 409->"Conflict";case 411->"Length Required";case 500->"Internal Server Error";default->"OK";};write(out,"HTTP/1.1 "+status+" "+reason+"\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nContent-Length: "+b.length+"\r\nConnection: close\r\n\r\n");out.write(b);}
    private void redirect(OutputStream out,String where,String token)throws IOException{String cookie=token==null?"":"Set-Cookie: session="+token+"; Path=/; HttpOnly; SameSite=Strict\r\n";write(out,"HTTP/1.1 303 See Other\r\nLocation: "+where+"\r\n"+cookie+"Content-Length: 0\r\nConnection: close\r\n\r\n");}
    private void download(OutputStream out,File file)throws IOException{String mime=Optional.ofNullable(java.net.URLConnection.guessContentTypeFromName(file.getName())).orElse("application/octet-stream");write(out,"HTTP/1.1 200 OK\r\nContent-Type: "+mime+"\r\nContent-Disposition: attachment; filename*=UTF-8''"+enc(file.getName())+"\r\nContent-Length: "+file.length()+"\r\nConnection: close\r\n\r\n");try(InputStream in=new FileInputStream(file)){byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);lastCommunicationNanos=System.nanoTime();}}}
    private Map<String,String> query(String raw){Map<String,String> m=new HashMap<>();if(raw==null)return m;for(String p:raw.split("&")){int x=p.indexOf('=');String k=x<0?p:p.substring(0,x),v=x<0?"":p.substring(x+1);m.put(URLDecoder.decode(k,StandardCharsets.UTF_8),URLDecoder.decode(v,StandardCharsets.UTF_8));}return m;}
    private byte[] readFully(InputStream in,int length)throws IOException{byte[] data=new byte[length];int offset=0;while(offset<length){int count=in.read(data,offset,length-offset);if(count<0)break;offset+=count;}return offset==length?data:Arrays.copyOf(data,offset);}
    private String enc(String s){return URLEncoder.encode(s==null?"":s,StandardCharsets.UTF_8).replace("+","%20");}
    private String escape(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    private void write(OutputStream o,String s)throws IOException{o.write(s.getBytes(StandardCharsets.UTF_8));}
}
