module com.mitb.recruitmenttracker {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.postgresql.jdbc;

    opens com.mitb.recruitmenttracker to javafx.fxml;
    exports com.mitb.recruitmenttracker;
}