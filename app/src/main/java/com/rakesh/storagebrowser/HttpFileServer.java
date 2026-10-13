package com.rakesh.storagebrowser;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

public class HttpFileServer {
    private final Context context; private final DocumentFile root; private final int port;
    private final String pin=String.format(Locale.US,"%06d",new SecureRandom().nextInt(1_000_000));
    private final Set<String> sessions=ConcurrentHashMap.newKeySet(); private volatile boolean running;
    private ServerSocket socket; private final ExecutorService pool=Executors.newFixedThreadPool(6);
    public HttpFileServer(Context c, Uri treeUri, int port){context=c.getApplicationContext();root=DocumentFile.fromTreeUri(c,treeUri);this.port=port;if(root==null)throw new IllegalArgumentException("Invalid folder");}
    public String getPin(){return pin;}
    public String getLanAddress(){
        try{Enumeration<java.net.NetworkInterface> ns=java.net.NetworkInterface.getNetworkInterfaces();while(ns.hasMoreElements()){Enumeration<InetAddress> as=ns.nextElement().getInetAddresses();while(as.hasMoreElements()){InetAddress a=as.nextElement();if(!a.isLoopbackAddress()&&a instanceof Inet4Address)return a.getHostAddress();}}}catch(Exception ignored){}return "127.0.0.1";
    }
    public void start() throws IOException{socket=new ServerSocket(port);running=true;new Thread(()->{while(running)try{Socket s=socket.accept();pool.execute(()->handle(s));}catch(IOException e){if(running)e.printStackTrace();}},"http-accept").start();}
    public void close(){running=false;try{if(socket!=null)socket.close();}catch(IOException ignored){}pool.shutdownNow();}
    private void handle(Socket s){try(s){s.setSoTimeout(30000);BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));String first=r.readLine();if(first==null)return;String[] f=first.split(" ");if(f.length<2)return;String method=f[0],target=f[1];Map<String,String> h=new HashMap<>();String line;while((line=r.readLine())!=null&&!line.isEmpty()){int x=line.indexOf(':');if(x>0)h.put(line.substring(0,x).toLowerCase(Locale.ROOT),line.substring(x+1).trim());}OutputStream out=s.getOutputStream();route(method,target,h,r,out);}catch(Exception ignored){}}
    private void route(String method,String target,Map<String,String> headers,BufferedReader reader,OutputStream out)throws Exception{
        URI uri=new URI(target);String path=uri.getPath();Map<String,String> q=query(uri.getRawQuery());
        if(path.equals("/login")&&method.equals("POST")){int n=Integer.parseInt(headers.getOrDefault("content-length","0"));char[] b=new char[Math.min(n,1024)];int got=reader.read(b);String supplied=query(new String(b,0,Math.max(0,got))).getOrDefault("pin","");if(pin.equals(supplied)){String token=UUID.randomUUID().toString();sessions.add(token);redirect(out,"/browse?path=",token);}else html(out,401,page("Incorrect PIN"));return;}
        if(!authorized(headers)){html(out,200,login());return;}
        if(path.equals("/browse")){DocumentFile dir=find(q.getOrDefault("path",""));if(dir==null||!dir.isDirectory()){html(out,404,page("Folder not found"));return;}html(out,200,browse(dir,q.getOrDefault("path","")));return;}
        if(path.equals("/download")){DocumentFile file=find(q.getOrDefault("path",""));if(file==null||!file.isFile()){html(out,404,page("File not found"));return;}download(out,file);return;}
        redirect(out,"/browse?path=",null);
    }
    private boolean authorized(Map<String,String> h){String c=h.getOrDefault("cookie","");for(String part:c.split(";")){part=part.trim();if(part.startsWith("session=")&&sessions.contains(part.substring(8)))return true;}return false;}
    private DocumentFile find(String encoded){String decoded=URLDecoder.decode(encoded,StandardCharsets.UTF_8);DocumentFile cur=root;if(decoded.isEmpty())return cur;for(String part:decoded.split("/")){if(part.isEmpty()||part.equals(".")||part.equals(".."))return null;cur=cur.findFile(part);if(cur==null)return null;}return cur;}
    private String browse(DocumentFile dir,String current){StringBuilder rows=new StringBuilder();DocumentFile[] files=dir.listFiles();Arrays.sort(files,Comparator.comparing((DocumentFile x)->!x.isDirectory()).thenComparing(x->Optional.ofNullable(x.getName()).orElse(""),String.CASE_INSENSITIVE_ORDER));for(DocumentFile x:files){String name=Optional.ofNullable(x.getName()).orElse("Unnamed");String next=current.isEmpty()?name:current+"/"+name;String href=x.isDirectory()?"/browse?path=":"/download?path=";rows.append("<div class='item'><div><b>").append(escape(name)).append("</b><small>").append(x.isDirectory()?"Folder":x.length()+" bytes").append("</small></div><a href='").append(href).append(enc(next)).append("'>").append(x.isDirectory()?"Open":"Download").append("</a></div>");}String parent="";int slash=current.lastIndexOf('/');if(slash>=0)parent=current.substring(0,slash);return page("<a href='/browse?path="+enc(parent)+"'>Parent</a><h1>Android storage</h1><div class='path'>/"+escape(current)+"</div><section>"+(rows.length()==0?"Folder is empty":rows)+"</section>");}
    private String login(){return page("<h1>Android Storage Browser</h1><p>Enter the six-digit PIN shown in the app.</p><form method='post' action='/login'><input name='pin' type='password' inputmode='numeric' maxlength='6' required autofocus><button>Connect</button></form><p class='warn'>Use only on trusted Wi-Fi. HTTP traffic is not encrypted.</p>");}
    private String page(String body){return "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width'><meta name='color-scheme' content='light dark'><style>:root{--b:#f4f6fa;--c:#fff;--t:#172033;--l:#d8dee8;--a:#087bea}*{box-sizing:border-box}body{margin:0;background:var(--b);color:var(--t);font:15px system-ui}main{width:min(900px,calc(100% - 20px));margin:auto;padding:20px}.item,section{background:var(--c);border:1px solid var(--l);border-radius:11px;padding:12px;margin:8px 0}.item{display:grid;grid-template-columns:1fr auto;gap:8px}.item small{display:block;opacity:.7}a,button{display:inline-block;padding:9px 12px;border:0;border-radius:8px;background:var(--a);color:white;text-decoration:none;font-weight:700}input{width:100%;padding:11px;margin:8px 0}.path{overflow-wrap:anywhere}.warn{color:#9a5a00}@media(prefers-color-scheme:dark){:root{--b:#101318;--c:#1b2028;--t:#eef2f8;--l:#3a4351}}</style></head><body><main>"+body+"</main></body></html>";}
    private void html(OutputStream out,int status,String body)throws IOException{byte[] b=body.getBytes(StandardCharsets.UTF_8);write(out,"HTTP/1.1 "+status+" OK\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nContent-Length: "+b.length+"\r\nConnection: close\r\n\r\n");out.write(b);}
    private void redirect(OutputStream out,String where,String token)throws IOException{String cookie=token==null?"":"Set-Cookie: session="+token+"; Path=/; HttpOnly; SameSite=Strict\r\n";write(out,"HTTP/1.1 303 See Other\r\nLocation: "+where+"\r\n"+cookie+"Content-Length: 0\r\nConnection: close\r\n\r\n");}
    private void download(OutputStream out,DocumentFile file)throws IOException{String mime=Optional.ofNullable(context.getContentResolver().getType(file.getUri())).orElse("application/octet-stream");write(out,"HTTP/1.1 200 OK\r\nContent-Type: "+mime+"\r\nContent-Disposition: attachment; filename*=UTF-8''"+enc(file.getName())+"\r\nContent-Length: "+file.length()+"\r\nConnection: close\r\n\r\n");try(InputStream in=context.getContentResolver().openInputStream(file.getUri())){if(in==null)return;byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}}
    private Map<String,String> query(String raw){Map<String,String> m=new HashMap<>();if(raw==null)return m;for(String p:raw.split("&")){int x=p.indexOf('=');String k=x<0?p:p.substring(0,x),v=x<0?"":p.substring(x+1);m.put(URLDecoder.decode(k,StandardCharsets.UTF_8),URLDecoder.decode(v,StandardCharsets.UTF_8));}return m;}
    private String enc(String s){return URLEncoder.encode(s==null?"":s,StandardCharsets.UTF_8).replace("+","%20");}
    private String escape(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private void write(OutputStream o,String s)throws IOException{o.write(s.getBytes(StandardCharsets.UTF_8));}
}
