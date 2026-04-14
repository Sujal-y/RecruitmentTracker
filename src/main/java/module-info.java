module com.mitb.recruitmenttracker {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;


    opens com.mitb.recruitmenttracker to javafx.fxml;
    exports com.mitb.recruitmenttracker;
}