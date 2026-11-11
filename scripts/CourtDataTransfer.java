import java.sql.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.security.MessageDigest;

/** Offline administration tool. No Spring startup, seed, DELETE, UPDATE, REPLACE or migration commands. */
public class CourtDataTransfer {
  record Table(String name, List<String> columns, List<List<Object>> rows, Set<String> parents, String ddl) {}
  static class Abort extends RuntimeException { Abort(String safeMessage) { super(safeMessage); } }
  static String id(String name) {
    if (!name.matches("[a-zA-Z0-9_]+")) throw new Abort("Unsupported database identifier.");
    return "`" + name + "`";
  }
  static String env(String name) {
    String value=System.getenv(name);
    if(value==null||value.isBlank()) throw new Abort("Missing environment variable: "+name);
    return value;
  }
  static Connection connect(String prefix) throws SQLException {
    return DriverManager.getConnection(env(prefix+"_DB_URL"),env(prefix+"_DB_USERNAME"),env(prefix+"_DB_PASSWORD"));
  }
  static List<String> names(Connection c) throws SQLException {
    var result=new ArrayList<String>();
    try(var r=c.getMetaData().getTables(c.getCatalog(),null,"%",new String[]{"TABLE"})) {
      while(r.next()) result.add(r.getString("TABLE_NAME"));
    }
    Collections.sort(result);
    return result;
  }
  static List<Table> snapshot(Connection c, boolean lock) throws SQLException {
    var result=new ArrayList<Table>(); int total=0;
    for(String name:names(c)) {
      var columns=new ArrayList<String>(); var rows=new ArrayList<List<Object>>(); var parents=new HashSet<String>();
      try(var r=c.getMetaData().getImportedKeys(c.getCatalog(),null,name)) {
        while(r.next()) if(!name.equals(r.getString("PKTABLE_NAME"))) parents.add(r.getString("PKTABLE_NAME"));
      }
      String ddl;
      try(var s=c.createStatement();var r=s.executeQuery("SHOW CREATE TABLE "+id(name))) {r.next();ddl=r.getString(2);}
      try(var s=c.createStatement();var r=s.executeQuery("SELECT * FROM "+id(name)+(lock?" FOR UPDATE":""))) {
        var meta=r.getMetaData();
        for(int i=1;i<=meta.getColumnCount();i++) columns.add(meta.getColumnName(i));
        while(r.next()) {
          if(++total>50000) throw new Abort("More than 50000 rows: use a reviewed large-data migration.");
          var row=new ArrayList<Object>();
          for(int i=1;i<=meta.getColumnCount();i++) {
            Object value=r.getObject(i);
            if(value instanceof Blob b) {value=b.getBytes(1,Math.toIntExact(b.length()));b.free();}
            row.add(value);
          }
          rows.add(row);
        }
      }
      result.add(new Table(name,columns,rows,parents,ddl));
    }
    return result;
  }
  static List<Table> ordered(List<Table> tables) {
    var pending=new ArrayList<>(tables);var done=new HashSet<String>();var result=new ArrayList<Table>();
    var all=new HashSet<String>();tables.forEach(t->all.add(t.name));
    while(!pending.isEmpty()) {
      boolean progress=false;
      for(var it=pending.iterator();it.hasNext();) {
        Table t=it.next();
        if(t.parents.stream().filter(all::contains).allMatch(done::contains)) {
          result.add(t);done.add(t.name);it.remove();progress=true;
        }
      }
      if(!progress) throw new Abort("Foreign-key cycle: automatic copy refused.");
    }
    return result;
  }
  static Table table(List<Table> tables,String name) {
    return tables.stream().filter(t->t.name.equals(name)).findFirst().orElseThrow(()->new Abort("Missing table: "+name));
  }
  static Object value(Table t,List<Object> row,String column) {return row.get(t.columns.indexOf(column));}
  static boolean business(Table t) {return !t.name.equals("flyway_schema_history");}
  static List<Table> payload(List<Table> tables) {
    return tables.stream().filter(CourtDataTransfer::business).filter(t->!t.name.equals("stored_uploads")).toList();
  }
  static void requireEmpty(List<Table> target) {
    for(Table t:target) if(business(t)&&!t.rows.isEmpty())
      throw new Abort("Target is NOT empty: "+t.name+" has "+t.rows.size()+" rows. No data was changed. Merge requires a separate ID/account mapping.");
  }
  static void validateSchema(List<Table> source,List<Table> target) {
    var a=new TreeSet<String>();var b=new TreeSet<String>();
    payload(source).forEach(t->a.add(t.name));payload(target).forEach(t->b.add(t.name));
    if(!a.equals(b)) throw new Abort("Business table sets differ; no automatic schema changes are allowed.");
    for(Table t:payload(source)) if(!t.columns.equals(table(target,t.name).columns))
      throw new Abort("Columns differ: "+t.name+". No automatic schema changes are allowed.");
    table(target,"stored_uploads");
    Table sf=table(source,"flyway_schema_history"),tf=table(target,"flyway_schema_history");
    for(var row:sf.rows) {
      Object version=value(sf,row,"version"); if(version==null) continue;
      var match=tf.rows.stream().filter(r->Objects.equals(version,value(tf,r,"version"))).findFirst()
          .orElseThrow(()->new Abort("Target is missing an applied source migration."));
      if(!Objects.equals(value(sf,row,"checksum"),value(tf,match,"checksum")) ||
          !Set.of("true","1").contains(String.valueOf(value(tf,match,"success")).toLowerCase(Locale.ROOT)))
        throw new Abort("Migration checksum/status mismatch; no migration history was changed.");
    }
  }
  static String literal(Object v) {
    if(v==null)return "NULL";
    if(v instanceof byte[] b)return "X'"+HexFormat.of().formatHex(b)+"'";
    if(v instanceof Boolean b)return b?"1":"0";
    if(v instanceof Number n)return n.toString();
    return "_utf8mb4 X'"+HexFormat.of().formatHex(v.toString().getBytes(StandardCharsets.UTF_8))+"'";
  }
  static void backupSql(Path file,List<Table> tables) throws IOException {
    try(var out=Files.newBufferedWriter(file,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
      out.write("-- Private snapshot. Restore ONLY to a NEW EMPTY database. Contains personal data.\n");
      out.write("SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS=0;\n");
      for(Table t:ordered(tables)) {
        out.write(t.ddl+";\n");
        for(var row:t.rows) {
          out.write("INSERT INTO "+id(t.name)+" ("+String.join(",",t.columns.stream().map(CourtDataTransfer::id).toList())+") VALUES (");
          for(int i=0;i<row.size();i++){if(i>0)out.write(",");out.write(literal(row.get(i)));}
          out.write(");\n");
        }
      }
      out.write("SET FOREIGN_KEY_CHECKS=1;\n-- Snapshot complete.\n");
    }
  }
  static String objectKey(Object owner,Object filename,String kind) {
    String name=String.valueOf(filename);
    if(!name.matches("[0-9a-fA-F-]{36}\\.(jpg|png|webp)")||!(owner instanceof Number))
      throw new Abort("Invalid image metadata; copy refused.");
    return kind+"/"+owner+"/"+name;
  }
  static Map<String,byte[]> originals(List<Table> source,Path root) throws Exception {
    var result=new LinkedHashMap<String,byte[]>();
    for(Table t:source) if(t.name.equals("stored_uploads")) for(var row:t.rows)
      result.put(String.valueOf(value(t,row,"object_key")),(byte[])value(t,row,"content"));
    var wanted=new LinkedHashSet<String>();
    Table images=table(source,"court_images");
    for(var row:images.rows) wanted.add(objectKey(value(images,row,"court_id"),value(images,row,"stored_name"),"courts"));
    Table accounts=table(source,"accounts");
    for(var row:accounts.rows) if(value(accounts,row,"payment_qr")!=null)
      wanted.add(objectKey(value(accounts,row,"id"),value(accounts,row,"payment_qr"),"payment-qr"));
    Path realRoot=root.toRealPath();
    for(String key:wanted) if(!result.containsKey(key)) {
      Path file=realRoot.resolve(key).normalize();
      if(!file.startsWith(realRoot)||!Files.isRegularFile(file)||!file.toRealPath().startsWith(realRoot))
        throw new Abort("Referenced image is missing or outside uploads; copy refused.");
      if(Files.size(file)>10*1024*1024) throw new Abort("An image exceeds the 10 MiB storage limit.");
      result.put(key,Files.readAllBytes(file));
    }
    long bytes=result.values().stream().mapToLong(b->b.length).sum();
    if(bytes>128L*1024*1024)throw new Abort("Images exceed this demo tool's 128 MiB limit.");
    System.out.println("Referenced/durable originals: "+result.size()+"; bytes: "+bytes);
    return result;
  }
  static void backupUploads(Path out,Path root,Map<String,byte[]> originals) throws Exception {
    try(var zip=new ZipOutputStream(Files.newOutputStream(out,StandardOpenOption.CREATE_NEW))) {
      Path real=root.toRealPath();
      try(var paths=Files.walk(real)) {
        for(Path file:paths.filter(Files::isRegularFile).toList()) {
          if(!file.toRealPath().startsWith(real))throw new Abort("Upload symlink outside root.");
          zip.putNextEntry(new ZipEntry("local-files/"+real.relativize(file).toString().replace('\\','/')));
          Files.copy(file,zip);zip.closeEntry();
        }
      }
      for(var item:originals.entrySet()) {
        zip.putNextEntry(new ZipEntry("mysql-originals/"+item.getKey()));
        zip.write(item.getValue());zip.closeEntry();
      }
    }
  }
  static void insert(Connection target,Table table) throws SQLException {
    String sql="INSERT INTO "+id(table.name)+" ("+String.join(",",table.columns.stream().map(CourtDataTransfer::id).toList())+
        ") VALUES ("+String.join(",",Collections.nCopies(table.columns.size(),"?"))+")";
    try(var p=target.prepareStatement(sql)) {
      for(var row:table.rows) {
        for(int i=0;i<row.size();i++) {if(row.get(i) instanceof byte[] b)p.setBytes(i+1,b);else p.setObject(i+1,row.get(i));}
        p.executeUpdate();
      }
    }
  }
  static void copy(Connection target,List<Table> source,Map<String,byte[]> images) throws Exception {
    for(Table t:ordered(payload(source))) insert(target,t);
    var rows=new ArrayList<List<Object>>();
    for(var e:images.entrySet())rows.add(Arrays.asList(e.getKey(),e.getValue()));
    insert(target,new Table("stored_uploads",List.of("object_key","content"),rows,Set.of(),""));
    for(Table t:payload(source))try(var s=target.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM "+id(t.name))) {
      r.next();if(r.getLong(1)!=t.rows.size())throw new Abort("Post-copy row count mismatch.");
    }
    try(var s=target.createStatement();var r=s.executeQuery("SELECT object_key,content FROM stored_uploads")) {
      int count=0;
      while(r.next()){
        byte[] expected=images.get(r.getString(1));
        if(expected==null||!MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(expected),
            MessageDigest.getInstance("SHA-256").digest(r.getBytes(2))))throw new Abort("Image checksum mismatch.");
        count++;
      }
      if(count!=images.size())throw new Abort("Image count mismatch.");
    }
  }
  static void report(Connection c,String label) throws SQLException {
    System.out.println(label+" database="+c.getCatalog()+"; table counts:");
    for(String t:names(c))if(!t.equals("flyway_schema_history"))
      try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM "+id(t))){r.next();System.out.println(t+"="+r.getLong(1));}
    if(names(c).contains("courts")) {
      try(var s=c.createStatement();var r=s.executeQuery("SELECT id,name,active,owner_id FROM courts ORDER BY id")) {
        while(r.next())System.out.println("court "+r.getLong(1)+" | "+r.getString(2)+" | active="+r.getBoolean(3)+" | owner="+r.getLong(4));
      }
    }
  }
  public static void main(String[] args) {
    String stage="start";
    try {
      String mode=args.length==0?"inspect":args[0];
      if(!Set.of("inspect","prepare","transfer").contains(mode))throw new Abort("Use inspect, prepare or transfer.");
      stage="source read";
      try(var source=connect("SOURCE")) {
        report(source,"LOCAL");
        if(mode.equals("inspect")) {
          stage="target TLS/read";
          try(var target=connect("TARGET")){target.setReadOnly(true);report(target,"AIVEN");}
          return;
        }
        source.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);source.setReadOnly(true);source.setAutoCommit(false);
        var data=snapshot(source,false);
        var images=originals(data,Path.of(env("SOURCE_UPLOAD_DIR")));
        Path out=Path.of(env("TRANSFER_BACKUP_DIR")).toAbsolutePath().normalize();Files.createDirectories(out);
        stage="source backup";
        backupSql(out.resolve("source.sql"),data);
        backupUploads(out.resolve("uploads.zip"),Path.of(env("SOURCE_UPLOAD_DIR")),images);
        source.rollback();
        System.out.println("Local snapshot/archives complete: "+out);
        if(mode.equals("prepare"))return;
        if(!"yes".equals(System.getenv("TRANSFER_APPLICATIONS_PAUSED")))throw new Abort("Pause both applications before transfer.");
        stage="target TLS/preflight/backup";
        try(var target=connect("TARGET")) {
          target.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);target.setAutoCommit(false);
          try {
            // Full-table locking reads prevent insertion races. Never disable FK checks on the target.
            var before=snapshot(target,true);
            backupSql(out.resolve("target-before.sql"),before);
            requireEmpty(before);
            validateSchema(data,before);
            stage="transactional INSERT/verification";
            try(var settings=target.createStatement()){settings.execute("SET SESSION sql_mode='STRICT_TRANS_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'");}
            copy(target,data,images);
            target.commit();
            System.out.println("Transfer committed. IDs/FKs preserved; row counts and all original image hashes verified.");
          } catch(Exception e){target.rollback();throw e;}
        }
      }
    } catch(Abort e){System.err.println("STOP: "+e.getMessage());System.exit(2);}
    catch(SQLException e){System.err.println("STOP at "+stage+". SQLState="+e.getSQLState()+" code="+e.getErrorCode()+". No credentials or SQL payload printed.");System.exit(3);}
    catch(Exception e){System.err.println("STOP at "+stage+". "+e.getClass().getSimpleName()+". No credentials or SQL payload printed.");System.exit(4);}
  }
}
