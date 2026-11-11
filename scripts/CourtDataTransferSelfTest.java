import java.sql.*;
import java.nio.file.*;
import java.util.*;
public class CourtDataTransferSelfTest {
  static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);}
  static CourtDataTransfer.Table t(String n,List<String> cols,List<List<Object>> rows,Set<String> parents){
    return new CourtDataTransfer.Table(n,cols,rows,parents,"CREATE TABLE "+n+" (id INT)");
  }
  public static void main(String[] args)throws Exception {
    try(var c=DriverManager.getConnection("jdbc:h2:mem:copy;MODE=MySQL;DATABASE_TO_LOWER=TRUE","sa","")){
      try(var s=c.createStatement()){
        s.execute("CREATE TABLE accounts(id BIGINT PRIMARY KEY,email VARCHAR(255),payment_qr VARCHAR(100))");
        s.execute("CREATE TABLE courts(id BIGINT PRIMARY KEY,owner_id BIGINT REFERENCES accounts(id))");
        s.execute("CREATE TABLE bookings(id BIGINT PRIMARY KEY,court_id BIGINT REFERENCES courts(id))");
        s.execute("CREATE TABLE stored_uploads(object_key VARCHAR(160) PRIMARY KEY,content LONGBLOB)");
      }
      var accounts=t("accounts",List.of("id","email","payment_qr"),List.of(Arrays.asList(7L,"fixture@test.vn",null)),Set.of());
      var courts=t("courts",List.of("id","owner_id"),List.of(List.of(20L,7L)),Set.of("accounts"));
      var bookings=t("bookings",List.of("id","court_id"),List.of(List.of(30L,20L)),Set.of("courts"));
      var data=List.of(bookings,courts,accounts);
      check(CourtDataTransfer.ordered(data).getFirst().name().equals("accounts"),"FK order");
      c.setAutoCommit(false);
      CourtDataTransfer.copy(c,data,Map.of("courts/20/test.png",new byte[]{1,2,3}));
      try(var s=c.createStatement();var r=s.executeQuery("SELECT owner_id FROM courts WHERE id=20")){
        r.next();check(r.getLong(1)==7,"preserved IDs");
      }
      try{CourtDataTransfer.requireEmpty(data);throw new AssertionError("must reject nonempty target");}
      catch(CourtDataTransfer.Abort expected){}
      c.rollback();
      try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM accounts")){
        r.next();check(r.getLong(1)==0,"rollback");
      }
      var broken=t("courts",courts.columns(),List.of(List.of(20L,999L)),courts.parents());
      try{CourtDataTransfer.copy(c,List.of(accounts,broken,bookings),Map.of());throw new AssertionError("FK must fail");}
      catch(SQLException expected){c.rollback();}
      try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM accounts")){
        r.next();check(r.getLong(1)==0,"FK failure rolls back");
      }
      Path out=Files.createTempFile("transfer-test-",".sql");Files.delete(out);
      CourtDataTransfer.backupSql(out,data);
      String backup=Files.readString(out);
      check(!backup.contains("DROP ")&&backup.contains("Snapshot complete"),"non-destructive snapshot");
      Files.delete(out);
      System.out.println("PASS: FK order, IDs, hashes, nonempty-target guard, rollback and backup format.");
    }
  }
}
