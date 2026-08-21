import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.Vector;

public class ThirdSem {

    private static final String DB_URL =
            System.getenv().getOrDefault("NDMA_DB_URL",
                    "jdbc:mysql://localhost:3306/dbms?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC");
    private static final String DB_USER =
            System.getenv().getOrDefault("NDMA_DB_USER", "root");
    private static final String DB_PASS =
            System.getenv().getOrDefault("NDMA_DB_PASS", "");

    private static final String FALLBACK_ADMIN = "admin";
    private static final String FALLBACK_PASS = "admin123";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            setModernUI();
            DBHelper db = new DBHelper(DB_URL, DB_USER, DB_PASS);
            if (!db.openConnection()) {
                JOptionPane.showMessageDialog(null, "Could not connect to DB. Check credentials.", "DB Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
            boolean loggedIn = showLoginDialog(db);
            if (!loggedIn) { db.close(); System.exit(0); }
            NDMAAdminSwing ui = new NDMAAdminSwing(db);
            ui.setVisible(true);
        });
    }

    private static void setModernUI() {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            UIManager.put("control", new Color(245, 247, 250));
            UIManager.put("text", new Color(40, 40, 40));
            UIManager.put("nimbusBase", new Color(70, 130, 180));
            UIManager.put("nimbusBlueGrey", new Color(220, 220, 220));
            UIManager.put("nimbusFocus", new Color(90, 150, 220));
        } catch (Exception ignored) {}
    }

    private static boolean showLoginDialog(DBHelper db) {
        JPanel panel = new JPanel(new GridLayout(0, 1, 6, 6));
        panel.setBorder(new EmptyBorder(12, 16, 12, 16));
        panel.setBackground(new Color(245, 247, 250));

        panel.add(new JLabel("👤 Username:"));
        JTextField userField = new JTextField();
        panel.add(userField);

        panel.add(new JLabel("🔒 Password:"));
        JPasswordField passField = new JPasswordField();
        panel.add(passField);

        int result = JOptionPane.showConfirmDialog(null, panel, "NDMA Admin Login",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return false;

        String user = userField.getText().trim();
        String pass = new String(passField.getPassword()).trim();

        try {
            if (db.adminTableExists()) {
                if (db.validateAdminLogin(user, pass)) return true;
                JOptionPane.showMessageDialog(null, "Invalid credentials.", "Login Failed", JOptionPane.ERROR_MESSAGE);
                return showLoginDialog(db);
            } else {
                if (FALLBACK_ADMIN.equals(user) && FALLBACK_PASS.equals(pass)) return true;
                JOptionPane.showMessageDialog(null, "Invalid credentials. Use admin/admin123", "Login Failed", JOptionPane.ERROR_MESSAGE);
                return showLoginDialog(db);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null, "Login error: " + ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    // ================= DB Helper =================
    public static class DBHelper {
        private final String url, user, pass;
        public Connection conn;

        public DBHelper(String url, String user, String pass) { this.url=url; this.user=user; this.pass=pass; }

        public boolean openConnection() {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                conn = DriverManager.getConnection(url,user,pass);
                return true;
            } catch(Exception e){ e.printStackTrace(); return false; }
        }

        public void close() {
            try { if(conn!=null && !conn.isClosed()) conn.close(); } catch(SQLException e){ e.printStackTrace(); }
        }

        public boolean adminTableExists() throws SQLException {
            DatabaseMetaData md = conn.getMetaData();
            try(ResultSet rs = md.getTables(conn.getCatalog(), null, "admin_login", new String[]{"TABLE"})) {
                return rs.next();
            }
        }

        public boolean validateAdminLogin(String username, String password) throws SQLException {
            String sql = "SELECT 1 FROM admin_login WHERE username=? AND password=?";
            try(PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, password);
                try(ResultSet rs = ps.executeQuery()){ return rs.next(); }
            }
        }

        public List<String> getTables() throws SQLException {
            List<String> tables = new ArrayList<>();
            DatabaseMetaData md = conn.getMetaData();
            try(ResultSet rs = md.getTables(conn.getCatalog(), null, "%", new String[]{"TABLE"})){
                while(rs.next()) tables.add(rs.getString("TABLE_NAME"));
            }
            Collections.sort(tables);
            return tables;
        }

        public TableMeta getTableMeta(String tableName) throws SQLException {
            String sql="SELECT * FROM "+escapeName(tableName)+" WHERE 1=0";
            try(PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()){
                ResultSetMetaData md = rs.getMetaData();
                int c = md.getColumnCount();
                String[] names = new String[c]; int[] types=new int[c];
                for(int i=0;i<c;i++){ names[i]=md.getColumnLabel(i+1); types[i]=md.getColumnType(i+1); }
                return new TableMeta(names, types);
            }
        }

        public String[] getPrimaryKeys(String tableName) throws SQLException {
            List<String> pks = new ArrayList<>();
            DatabaseMetaData md = conn.getMetaData();
            try(ResultSet rs = md.getPrimaryKeys(conn.getCatalog(), null, tableName)){
                while(rs.next()) pks.add(rs.getString("COLUMN_NAME"));
            }
            return pks.toArray(new String[0]);
        }

        public int insertRow(String tableName,String[] cols,Object[] values,int[] types) throws SQLException {
            StringBuilder sbCols=new StringBuilder(), sbVals=new StringBuilder();
            for(int i=0;i<cols.length;i++){
                if(i>0){ sbCols.append(", "); sbVals.append(", "); }
                sbCols.append(escapeName(cols[i])); sbVals.append("?");
            }
            String sql="INSERT INTO "+escapeName(tableName)+" ("+sbCols+") VALUES ("+sbVals+")";
            try(PreparedStatement ps = conn.prepareStatement(sql)){
                for(int i=0;i<values.length;i++) setPreparedStatementValue(ps,i+1,types[i],values[i]);
                return ps.executeUpdate();
            }
        }

        public int updateRowByPK(String tableName,String[] cols,Object[] values,int[] types,String[] pkCols,Object[] pkValues,int[] pkTypes) throws SQLException {
            if(pkCols.length==0) throw new SQLException("No PK defined");
            StringBuilder sb=new StringBuilder(), where=new StringBuilder();
            for(int i=0;i<cols.length;i++){ if(i>0) sb.append(", "); sb.append(escapeName(cols[i])+"=?"); }
            for(int i=0;i<pkCols.length;i++){ if(i>0) where.append(" AND "); where.append(escapeName(pkCols[i])+"=?"); }
            String sql="UPDATE "+escapeName(tableName)+" SET "+sb+" WHERE "+where;
            try(PreparedStatement ps = conn.prepareStatement(sql)){
                int idx=1; for(int i=0;i<values.length;i++) setPreparedStatementValue(ps,idx++,types[i],values[i]);
                for(int i=0;i<pkValues.length;i++) setPreparedStatementValue(ps,idx++,pkTypes[i],pkValues[i]);
                return ps.executeUpdate();
            }
        }

        public int deleteRowByPK(String tableName,String[] pkCols,Object[] pkValues,int[] pkTypes) throws SQLException {
            if(pkCols.length==0) throw new SQLException("No PK defined");
            StringBuilder where=new StringBuilder();
            for(int i=0;i<pkCols.length;i++){ if(i>0) where.append(" AND "); where.append(escapeName(pkCols[i])+"=?"); }
            String sql="DELETE FROM "+escapeName(tableName)+" WHERE "+where;
            try(PreparedStatement ps = conn.prepareStatement(sql)){
                for(int i=0;i<pkValues.length;i++) setPreparedStatementValue(ps,i+1,pkTypes[i],pkValues[i]);
                return ps.executeUpdate();
            }
        }

        public DefaultTableModel getTableData(String tableName) throws SQLException {
            String sql="SELECT * FROM "+escapeName(tableName);
            try(PreparedStatement ps=conn.prepareStatement(sql); ResultSet rs=ps.executeQuery()){
                return buildTableModel(rs);
            }
        }

        public static DefaultTableModel buildTableModel(ResultSet rs) throws SQLException {
            ResultSetMetaData meta = rs.getMetaData();
            int colCount=meta.getColumnCount();
            Vector<String> colNames=new Vector<>();
            for(int i=1;i<=colCount;i++) colNames.add(meta.getColumnLabel(i));
            Vector<Vector<Object>> data=new Vector<>();
            while(rs.next()){
                Vector<Object> row=new Vector<>();
                for(int i=1;i<=colCount;i++) row.add(rs.getObject(i));
                data.add(row);
            }
            return new DefaultTableModel(data,colNames){ public boolean isCellEditable(int r,int c){ return false; } };
        }

        private void setPreparedStatementValue(PreparedStatement ps,int idx,int type,Object val) throws SQLException {
            if(val==null || (val instanceof String && ((String)val).isEmpty())) ps.setNull(idx,type==0?Types.VARCHAR:type);
            else{
                switch(type){
                    case Types.INTEGER: case Types.SMALLINT: case Types.TINYINT: ps.setInt(idx,Integer.parseInt(val.toString())); break;
                    case Types.BIGINT: ps.setLong(idx,Long.parseLong(val.toString())); break;
                    case Types.FLOAT: case Types.REAL: ps.setFloat(idx,Float.parseFloat(val.toString())); break;
                    case Types.DOUBLE: case Types.NUMERIC: case Types.DECIMAL: ps.setDouble(idx,Double.parseDouble(val.toString())); break;
                    case Types.BOOLEAN: case Types.BIT: ps.setBoolean(idx,Boolean.parseBoolean(val.toString())); break;
                    case Types.DATE: ps.setDate(idx,java.sql.Date.valueOf(val.toString())); break;
                    case Types.TIME: ps.setTime(idx,java.sql.Time.valueOf(val.toString())); break;
                    case Types.TIMESTAMP: ps.setTimestamp(idx,java.sql.Timestamp.valueOf(val.toString())); break;
                    default: ps.setString(idx,val.toString());
                }
            }
        }

        private String escapeName(String n){ if(n==null) return null; n=n.trim(); if(n.startsWith("`")&&n.endsWith("`")) return n; return "`"+n.replace("`","")+"`"; }

        // ========== Inner class for Table Metadata ==========
        public static class TableMeta{
            public final String[] colNames; public final int[] colTypes;
            public TableMeta(String[] n,int[] t){ colNames=n; colTypes=t; }
        }
    } // end DBHelper

    // ================== Admin Swing ==================
    public static class NDMAAdminSwing extends JFrame{
        private final DBHelper db;
        private DefaultListModel<String> listModel;
        private JList<String> tableList;
        private JTable dataTable;
        private JLabel statusLabel;
        private String currentTable;

        public NDMAAdminSwing(DBHelper db){
            super("🌐 NDMA Admin Dashboard");
            this.db=db;
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(1300,700);
            setLocationRelativeTo(null);
            initUI();
            loadTables();
        }

        private void initUI(){
            getContentPane().setLayout(new BorderLayout());
            getContentPane().setBackground(new Color(245,247,250));

            JLabel header=new JLabel("NDMA ADMIN DASHBOARD",SwingConstants.CENTER);
            header.setFont(new Font("Segoe UI",Font.BOLD,22));
            header.setOpaque(true);
            header.setBackground(new Color(60,130,200));
            header.setForeground(Color.WHITE);
            header.setBorder(new EmptyBorder(12,10,12,10));
            add(header,BorderLayout.NORTH);

            listModel=new DefaultListModel<>();
            tableList=new JList<>(listModel);
            tableList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            tableList.setFont(new Font("Segoe UI",Font.PLAIN,14));
            JScrollPane side=new JScrollPane(tableList);
            side.setPreferredSize(new Dimension(250,0));
            side.setBorder(BorderFactory.createTitledBorder("📋 Tables"));
            add(side,BorderLayout.WEST);

            dataTable=new JTable(); dataTable.setFont(new Font("Segoe UI",Font.PLAIN,13)); dataTable.setRowHeight(26);
            JScrollPane center=new JScrollPane(dataTable);
            center.setBorder(BorderFactory.createTitledBorder("📊 Table Data"));
            add(center,BorderLayout.CENTER);

            JPanel bottom=new JPanel(new BorderLayout());
            JPanel btnPanel=new JPanel(new FlowLayout(FlowLayout.CENTER,16,8));
            JButton btnInsert=makeButton("➕ Insert");
            JButton btnUpdate=makeButton("✏️ Update");
            JButton btnDelete=makeButton("🗑️ Delete");
            JButton btnRefresh=makeButton("🔄 Refresh");
            JButton btnResolve=makeButton("✅ Resolve Disaster");
            btnPanel.add(btnInsert); btnPanel.add(btnUpdate); btnPanel.add(btnDelete); btnPanel.add(btnRefresh); btnPanel.add(btnResolve);
            statusLabel=new JLabel("Ready"); statusLabel.setBorder(new EmptyBorder(6,10,6,10));
            bottom.add(btnPanel,BorderLayout.CENTER); bottom.add(statusLabel,BorderLayout.SOUTH);
            bottom.setBackground(new Color(245,247,250));
            add(bottom,BorderLayout.SOUTH);

            tableList.addListSelectionListener(e->{
                if(!e.getValueIsAdjusting()){ currentTable=tableList.getSelectedValue(); refreshCurrentTable(); }
            });

            btnRefresh.addActionListener(e->{ if(currentTable==null){ showInfo("Select a table first."); return; } refreshCurrentTable(); });
            btnInsert.addActionListener(e->handleInsert());
            btnUpdate.addActionListener(e->handleUpdate());
            btnDelete.addActionListener(e->handleDelete());
            btnResolve.addActionListener(e->handleResolveDisaster());

            dataTable.addMouseListener(new MouseAdapter(){ public void mouseClicked(MouseEvent e){ if(e.getClickCount()==2) handleUpdate(); }});
        }

        private JButton makeButton(String t){ JButton b=new JButton(t); b.setFocusPainted(false); b.setBackground(new Color(70,130,180)); b.setForeground(Color.WHITE);
            b.setFont(new Font("Segoe UI",Font.BOLD,13)); b.setBorder(new EmptyBorder(8,14,8,14)); b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            b.addMouseListener(new MouseAdapter(){ public void mouseEntered(MouseEvent e){ b.setBackground(new Color(90,150,220)); } public void mouseExited(MouseEvent e){ b.setBackground(new Color(70,130,180)); }});
            return b;
        }

        private void loadTables(){
            try{ listModel.clear(); for(String t:db.getTables()) listModel.addElement(t); statusLabel.setText("Loaded "+listModel.size()+" tables."); }
            catch(SQLException ex){ showError("Error loading tables: "+ex.getMessage()); }
        }

        private void refreshCurrentTable(){
            if(currentTable==null) return;
            try{ dataTable.setModel(db.getTableData(currentTable)); statusLabel.setText("Showing: "+currentTable); }
            catch(SQLException ex){ showError("Error loading table data: "+ex.getMessage()); }
        }

        // ================= CRUD Operations =================
        private void handleInsert(){ 
            if(currentTable==null){ showInfo("Select a table first."); return; } 
            try{
                DBHelper.TableMeta tm=db.getTableMeta(currentTable);
                JPanel p=new JPanel(new GridLayout(0,2,6,6)); p.setBorder(new EmptyBorder(8,8,8,8));
                JTextField[] fields=new JTextField[tm.colNames.length];
                for(int i=0;i<tm.colNames.length;i++){ p.add(new JLabel(tm.colNames[i]+" :")); fields[i]=new JTextField(); p.add(fields[i]); }
                int ok=JOptionPane.showConfirmDialog(this,new JScrollPane(p),"Insert Row",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
                if(ok!=JOptionPane.OK_OPTION) return;
                Object[] vals=new Object[tm.colNames.length]; int[] types=tm.colTypes;
                for(int i=0;i<vals.length;i++) vals[i]=fields[i].getText().trim();
                db.insertRow(currentTable,tm.colNames,vals,types);
                refreshCurrentTable();
                showInfo("Row inserted.");
            } catch(Exception ex){ showError("Insert failed: "+ex.getMessage()); }
        }

        private void handleUpdate(){
            int selRow=dataTable.getSelectedRow();
            if(selRow==-1){ showInfo("Select a row to update."); return; }
            selRow=dataTable.convertRowIndexToModel(selRow);
            try{
                DBHelper.TableMeta tm=db.getTableMeta(currentTable);
                String[] cols=tm.colNames; int[] types=tm.colTypes;
                String[] pkCols=db.getPrimaryKeys(currentTable);
                if(pkCols.length==0){ showInfo("No primary key defined."); return; }
                Object[] pkVals=new Object[pkCols.length]; int[] pkTypes=new int[pkCols.length];
                for(int i=0;i<pkCols.length;i++){ int idx=findColumnIndex(cols,pkCols[i]); pkVals[i]=dataTable.getModel().getValueAt(selRow,idx); pkTypes[i]=types[idx]; }
                JPanel p=new JPanel(new GridLayout(0,2,6,6)); p.setBorder(new EmptyBorder(8,8,8,8));
                JTextField[] fields=new JTextField[cols.length];
                for(int i=0;i<cols.length;i++){ Object val=dataTable.getModel().getValueAt(selRow,i); fields[i]=new JTextField(val==null?"":val.toString()); p.add(new JLabel(cols[i]+" :")); p.add(fields[i]); }
                int ok=JOptionPane.showConfirmDialog(this,new JScrollPane(p),"Update Row",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
                if(ok!=JOptionPane.OK_OPTION) return;
                Object[] newVals=new Object[cols.length]; for(int i=0;i<cols.length;i++) newVals[i]=fields[i].getText().trim();
                db.updateRowByPK(currentTable,cols,newVals,types,pkCols,pkVals,pkTypes);
                showInfo("Row updated."); refreshCurrentTable();
            } catch(Exception ex){ showError("Update failed: "+ex.getMessage()); }
        }

        private void handleDelete(){
            int selRow=dataTable.getSelectedRow();
            if(selRow==-1){ showInfo("Select a row to delete."); return; }
            selRow=dataTable.convertRowIndexToModel(selRow);
            try{
                String[] pkCols=db.getPrimaryKeys(currentTable);
                if(pkCols.length==0){ showInfo("No primary key defined."); return; }
                DBHelper.TableMeta tm=db.getTableMeta(currentTable); String[] cols=tm.colNames; int[] types=tm.colTypes;
                Object[] pkVals=new Object[pkCols.length]; int[] pkTypes=new int[pkCols.length];
                for(int i=0;i<pkCols.length;i++){ int idx=findColumnIndex(cols,pkCols[i]); pkVals[i]=dataTable.getModel().getValueAt(selRow,idx); pkTypes[i]=types[idx]; }
                int confirm=JOptionPane.showConfirmDialog(this,"Delete selected row?","Confirm Delete",JOptionPane.YES_NO_OPTION);
                if(confirm!=JOptionPane.YES_OPTION) return;
                db.deleteRowByPK(currentTable,pkCols,pkVals,pkTypes);
                showInfo("Row deleted."); refreshCurrentTable();
            } catch(Exception ex){ showError("Delete failed: "+ex.getMessage()); }
        }

        // ================= Resolve Disaster Button =================
        private ActionListener handleResolveDisaster(){
            return e->{
                if(!"disaster_address".equalsIgnoreCase(currentTable)){
                    showInfo("Resolve Disaster is only available for disaster_address table.");
                    return;
                }
                int selRow=dataTable.getSelectedRow();
                if(selRow==-1){ showInfo("Select a disaster to resolve."); return; }
                selRow=dataTable.convertRowIndexToModel(selRow);
                try{
                    DBHelper.TableMeta tm=db.getTableMeta(currentTable);
                    String[] cols=tm.colNames; int[] types=tm.colTypes;
                    String[] pkCols=db.getPrimaryKeys(currentTable);
                    Object[] pkVals=new Object[pkCols.length]; int[] pkTypes=new int[pkCols.length];
                    for(int i=0;i<pkCols.length;i++){ int idx=findColumnIndex(cols,pkCols[i]); pkVals[i]=dataTable.getModel().getValueAt(selRow,idx); pkTypes[i]=types[idx]; }
                    // Update status to Closed, trigger will automatically log and delete from disaster table
                    int statusIdx=findColumnIndex(cols,"status");
                    db.updateRowByPK(currentTable,new String[]{"status"}, new Object[]{"Closed"}, new int[]{Types.VARCHAR}, pkCols, pkVals, pkTypes);
                    showInfo("Disaster marked as Closed. Trigger executed for logging & deletion.");
                    refreshCurrentTable();
                } catch(Exception ex){ showError("Resolve failed: "+ex.getMessage()); }
            };
        }

        private int findColumnIndex(String[] cols,String target){ for(int i=0;i<cols.length;i++) if(cols[i].equalsIgnoreCase(target)) return i; return -1; }
        private void showInfo(String m){ statusLabel.setText(m); JOptionPane.showMessageDialog(this,m,"Info",JOptionPane.INFORMATION_MESSAGE); }
        private void showError(String m){ statusLabel.setText("Error: "+m); JOptionPane.showMessageDialog(this,m,"Error",JOptionPane.ERROR_MESSAGE); }
    } // end NDMAAdminSwing
}
