package model;

import java.io.Serializable;
import java.sql.Date; // Lưu ý import gói sql

public class User implements Serializable {
    private int id;
    private String name;
    private String email;
    private String password;
    private String gender; // 'Male', 'Female', 'Other'
    private Date dob;      // Ngày sinh (java.sql.Date)

    public User() {}

    // Constructor đầy đủ
    public User(int id, String name, String email, String gender, Date dob) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.gender = gender;
        this.dob = dob;
    }

    // Getter & Setter
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public Date getDob() { return dob; }
    public void setDob(Date dob) { this.dob = dob; }
}