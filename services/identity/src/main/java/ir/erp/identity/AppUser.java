package ir.erp.identity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity @Table(name="app_users")
public class AppUser {
  @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
  @Column(nullable=false,unique=true,length=80) public String username;
  @Column(name="password_hash",nullable=false,length=100) public String passwordHash;
  @Column(name="display_name",nullable=false,length=160) public String displayName;
  @Column(nullable=false) public boolean active=true;
  @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="user_roles",joinColumns=@JoinColumn(name="user_id"))
  @Column(name="role",nullable=false) public Set<String> roles=new HashSet<>();
  @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="user_holdings",joinColumns=@JoinColumn(name="user_id"))
  @Column(name="holding_id",nullable=false,length=36) public Set<String> holdingIds=new HashSet<>();
  @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="user_companies",joinColumns=@JoinColumn(name="user_id"))
  @Column(name="company_id",nullable=false,length=36) public Set<String> companyIds=new HashSet<>();
}
