package net.miotest.client;

import java.util.ArrayList;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Platform-independent node collection; credentials are encrypted by NodeStore. */
public final class NodeBook {
 public static final class Node {
  public String id,name,server,dest,password;
  public int port,destPort;
  public long revision;
  public Node(String id,String name,String server,int port,String dest,int destPort,String password,long revision){
   this.id=id;this.name=name;this.server=server;this.port=port;this.dest=dest;this.destPort=destPort;this.password=password;this.revision=revision;
  }
  public static Node create(String name,String server,int port,String dest,int destPort,String password){return new Node(UUID.randomUUID().toString(),name,server,port,dest,destPort,password,System.currentTimeMillis());}
  public void validate(){
   name=name.trim();server=server.trim();dest=dest.trim();
   if(server.startsWith("[")&&server.endsWith("]"))server=server.substring(1,server.length()-1);
   if(dest.startsWith("[")&&dest.endsWith("]"))dest=dest.substring(1,dest.length()-1);
   if(name.isEmpty()||name.length()>60)throw new IllegalArgumentException("节点名称需要 1–60 个字符");
   if(!host(server))throw new IllegalArgumentException("请输入服务器 IP 或域名，不要带 http:// 或端口");
   if(!host(dest))throw new IllegalArgumentException("请输入有效的伪装域名或 IP");
   if(port<1||port>65535||destPort<1||destPort>65535)throw new IllegalArgumentException("端口范围为 1–65535");
   if(password.isEmpty())throw new IllegalArgumentException("请填写连接密码");
  }
  private static boolean host(String value){return !value.isEmpty()&&value.length()<254&&!value.matches(".*[\\s/\\\\?#].*")&&(!value.contains(":")||value.indexOf(':')!=value.lastIndexOf(':'));}
  public String address(){return (server.contains(":")?"["+server+"]":server)+":"+port;}
  public JSONObject json() throws Exception {return new JSONObject().put("id",id).put("name",name).put("server",server).put("port",port).put("dest",dest).put("destPort",destPort).put("password",password).put("revision",revision);}
  public static Node parse(JSONObject j)throws Exception {Node n=new Node(j.getString("id"),j.getString("name"),j.getString("server"),j.getInt("port"),j.getString("dest"),j.getInt("destPort"),j.getString("password"),j.optLong("revision",1));n.validate();return n;}
 }
 private final ArrayList<Node> nodes=new ArrayList<>();
 private String selected="";
 public ArrayList<Node> nodes(){return new ArrayList<>(nodes);}
 public Node get(String id){for(Node n:nodes)if(n.id.equals(id))return n;return null;}
 public Node selected(){return get(selected);}
 public void select(String id){if(get(id)==null)throw new IllegalArgumentException("节点不存在");selected=id;}
 public void put(Node node){node.validate();for(int i=0;i<nodes.size();i++)if(nodes.get(i).id.equals(node.id)){nodes.set(i,node);return;}nodes.add(node);if(selected.isEmpty())selected=node.id;}
 public void remove(String id){for(int i=nodes.size()-1;i>=0;i--)if(nodes.get(i).id.equals(id))nodes.remove(i);if(selected.equals(id))selected=nodes.isEmpty()?"":nodes.get(0).id;}
 public String encode()throws Exception {JSONArray a=new JSONArray();for(Node n:nodes)a.put(n.json());return new JSONObject().put("version",1).put("selected",selected).put("nodes",a).toString();}
 public static NodeBook decode(String value)throws Exception {
  NodeBook b=new NodeBook();JSONObject j=new JSONObject(value);if(j.getInt("version")!=1)throw new IllegalArgumentException("不支持的节点数据版本");
  JSONArray a=j.getJSONArray("nodes");for(int i=0;i<a.length();i++){Node n=Node.parse(a.getJSONObject(i));if(b.get(n.id)!=null)throw new IllegalArgumentException("节点 ID 重复");b.put(n);}
  String s=j.optString("selected","");if(b.get(s)!=null)b.select(s);return b;
 }
}
