# ClassHack — Hackathon Registration & Team Management System

> **A centralized, secure web-based platform for college classes, departments, and sections to manage student hackathon participation, internal team registrations, status updates, and long-term student achievement records.**

---

## 1. Project Overview

**ClassHack** is a full-stack Java Spring Boot and MySQL web application designed to eliminate scattered spreadsheets, manual documentation, and messaging-based tracking of college hackathons.

### Core Philosophy: The Admin-Controlled Model
* **Students CANNOT independently register accounts.**
* Authorized administrators (Innovation Coordinators, Faculty Advisors, Class Coordinators, HODs) create and manage student accounts and assign credentials.
* Multiple administrators can collaborate within the same class, department, or section.
* Students login, explore hackathons, register officially via external portals, and return to record their internal participation teams with registered classmates.
* Administrators update team competition stages (Registered, Shortlisted, Next Round, Finalist, Winner, Eliminated, Withdrawn), and the system automatically maintains the class statistics, audit trails, and long-term student hackathon portfolios.

---

## 2. Technology Stack

* **Backend Framework:** Spring Boot 4.1.1 (Java 25 LTS)
* **Build System:** Apache Maven 3.9+
* **Core Modules:**
  * Spring Web (Spring MVC REST API)
  * Spring Data JPA (Hibernate 7 ORM)
  * Spring Security (Role-based access control with `ROLE_ADMIN` and `ROLE_STUDENT`)
  * Jakarta Bean Validation
* **Database:** MySQL 8
* **Security & Encryption:** BCrypt Password Hashing, HTTP Session authentication, API role guards
* **Frontend:** HTML5, Modern CSS3 with custom design tokens, JavaScript (Vanilla ES6+ Fetch API), Bootstrap 5.3, Bootstrap Icons, Google Fonts (Inter & Outfit)
* **Reporting:** RFC 4180 compliant CSV export engine

---

## 3. Key Features

1. **Admin-Controlled Security:** No public student registration; only verified students can log in.
2. **Multi-Admin Class Support:** Shared administration across Innovation Coordinators, Faculty, and Class In-charges.
3. **Dynamic Hackathon Lifecycle:** Automatic status calculation (`UPCOMING`, `REGISTRATION_OPEN`, `REGISTRATION_CLOSED`, `COMPLETED`) based on start and end dates.
4. **Backend-Enforced Team Rules:**
   * Student can participate in multiple different hackathons.
   * Student **cannot** belong to two teams in the same hackathon.
   * Team size cannot exceed maximum limit.
   * Team name must be unique within that hackathon.
   * Only existing registered students can be added (search & select, no free text errors).
   * Team leader is automatically a member.
   * Team must belong to exactly one hackathon.
   * No team registration or member addition after deadline.
   * Duplicate members prohibited.
5. **Team Status Tracking & In-App Notifications:** Real-time updates from `REGISTERED` to `SHORTLISTED`, `NEXT_ROUND`, `FINALIST`, `WINNER`, etc., sending in-app alerts to team members.
6. **Live Dashboard Statistics:** Computed directly from MySQL database:
   * Total Students
   * Total Hackathons
   * Open Hackathons
   * Closed Hackathons
   * Total Teams
   * Total Participants
   * Shortlisted Teams
   * Finalists
   * Winners
7. **Student Participation History & Portfolio:** Long-term achievement tracking for accreditation (NAAC, NBA), student placement resumes, and department records.
8. **Student Privacy Protection:** Students can only view their own teams, profiles, and history. Other teams are hidden at the database/API level.
9. **One-Click Export:** Detailed CSV export for any hackathon and summary CSV export for all events.
10. **Audit Logging:** System trail recording administrative student creations, status changes, and deletions.

---

## 4. Database Structure

Database Name: `classhack`

### Main Relational Entities
* `admins`: Administrator profiles, credentials, department, section, position, active status.
* `students`: Registered student records, student ID/roll number, credentials, department, section, year level, academic year, active status.
* `hackathons`: Competition records, dates, mode (ONLINE/OFFLINE), venue, prize pool, team size, official registration link, computed status.
* `teams`: Registered teams linked to hackathons and team leaders, tracking status and soft deletion.
* `team_members`: Join table linking teams and students with assigned roles ("Leader", "Member"). Unique constraint on `(team_id, student_id)`.
* `notifications`: Student in-app notifications for status updates and team additions.
* `audit_logs`: Timestamped trail of admin actions.

---

## 5. MySQL Setup & Configuration

### Step 1: Create Database
Open MySQL Command Line or MySQL Workbench:
```sql
CREATE DATABASE classhack;
```

### Step 2: Configure Environment or application.properties
The application supports standard environment variables or fallback values in `src/main/resources/application.properties`:
* `DB_URL`: `jdbc:mysql://localhost:3306/classhack?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`
* `DB_USERNAME`: `root` (or your MySQL user)
* `DB_PASSWORD`: your MySQL root password (leave empty if no password is set)

Example via Windows PowerShell:
```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_password_here"
```

Tables and constraints are generated and updated automatically on startup via `spring.jpa.hibernate.ddl-auto=update`.

---

## 6. How to Run

### Option A: From IntelliJ IDEA (Recommended)
1. Open IntelliJ IDEA.
2. Open the project root folder `c:\Users\ADHISHESAGAN\Downloads\ClassHack`.
3. IntelliJ will detect `ClassHack/pom.xml`.
4. Run the preconfigured Spring Boot run configuration:
   * Main Class: `com.classhack.ClassHack.ClassHackApplication` (or `com.classhack.ClassHackApplication`)
   * JRE: Microsoft OpenJDK 25 (`ms-25.0.4.1`)
5. Click the green **Run** or **Debug** button.

### Option B: Using Maven Wrapper
Open terminal / PowerShell in `ClassHack/ClassHack`:
```powershell
$env:JAVA_HOME="C:\Users\ADHISHESAGAN\.jdks\ms-25.0.4.1"
.\mvnw.cmd spring-boot:run
```

---

## 7. Application URL & Complete User Flow

* **URL:** [http://localhost:8080](http://localhost:8080)

### 1. Admin Workflow
1. Open `http://localhost:8080`.
2. Click **Admin Registration** (`/register-admin.html`).
3. Fill in Full Name, Phone, Official Email, Department (e.g. ECE), Section (e.g. A), Position (e.g. Innovation Coordinator), Password.
4. Log in using the **Admin Login** tab.
5. On the **Admin Dashboard**, click **Add Student**:
   * Enter Student ID (e.g. `ECE001`), Name, Department (`ECE`), Section (`A`), Year (`III`), Academic Year (`2026-27`), Email, Phone, Initial Password.
6. Click **New Hackathon**:
   * Enter Name (e.g. `Smart India Hackathon 2026`), Organization (`Govt of India`), Mode (`ONLINE` or `OFFLINE`), Dates, Team Size (`6`), Official Link (`https://sih.gov.in`).
7. Switch to **Teams & Status** view:
   * Select the hackathon.
   * View registered teams and structured student member tables.
   * Click **Update Status** to advance a team to `SHORTLISTED`, `FINALIST`, `WINNER`, etc.
8. Switch to **Reports & Export** to download the CSV report.

### 2. Student Workflow
1. Log in on `http://localhost:8080` using the **Student Login** tab with the credentials provided by the administrator (Student ID or Email + Password).
2. On the **Student Dashboard**:
   * View available hackathons.
   * Click **Official Portal Registration** to register externally.
   * Click **Create Internal Team**:
     * Enter Team Name (e.g. `Electro Nova`).
     * Search and select classmate members by Student ID or Name.
     * Click **Register Team**.
3. Open **My Teams & Status** to view your team's real-time progress and notifications.
4. Open **Participation History** to see your long-term achievement portfolio.

---

## 8. REST API Summary

### Authentication
* `POST /api/auth/register-admin`: Register an administrator account.
* `POST /api/auth/login`: Unified login for Admin and Student.
* `POST /api/auth/logout`: Invalidate session and logout.
* `GET /api/auth/me`: Current session profile inspection.
* `POST /api/auth/change-password`: Update account password.

### Admin Operations (`ROLE_ADMIN`)
* `POST /api/admin/students`: Create student account.
* `GET /api/admin/students`: List all students.
* `GET /api/admin/students/search?keyword=...`: Search students.
* `GET /api/admin/students/{id}`: Student details.
* `PUT /api/admin/students/{id}`: Update student information.
* `POST /api/admin/students/{id}/toggle-status`: Activate/deactivate student.
* `POST /api/admin/students/{id}/reset-password`: Reset student password.
* `GET /api/admin/students/{id}/history`: View student hackathon portfolio.
* `GET /api/admin/dashboard-stats`: Live 9-metric dashboard statistics.
* `GET /api/admin/audit-logs`: Administrative audit trail.

### Hackathons
* `POST /api/hackathons`: Create new hackathon (Admin).
* `GET /api/hackathons`: List all hackathons with computed status.
* `GET /api/hackathons/search?keyword=...`: Search hackathons.
* `GET /api/hackathons/{id}`: Hackathon details.
* `PUT /api/hackathons/{id}`: Update hackathon (Admin).
* `DELETE /api/hackathons/{id}`: Soft delete hackathon (Admin).

### Teams & Members
* `POST /api/hackathons/{id}/teams`: Create internal team (Student leader).
* `GET /api/hackathons/{id}/teams`: Get teams (Admin sees all, Student sees own).
* `GET /api/teams/{id}`: Get team details.
* `PUT /api/teams/{id}/status`: Update team status & notify members (Admin).
* `POST /api/teams/{id}/members`: Add team member.
* `DELETE /api/teams/{id}/members/{studentId}`: Remove team member.
* `DELETE /api/teams/{id}`: Delete team.

### Student Operations (`ROLE_STUDENT`)
* `GET /api/student/profile`: Current student's profile.
* `GET /api/student/teams`: Current student's teams.
* `GET /api/student/history`: Current student's participation history.
* `GET /api/student/search-members?query=...`: Search registered classmates.
* `GET /api/student/notifications`: In-app notification alerts.
* `POST /api/student/notifications/read-all`: Mark notifications as read.

### Reports
* `GET /api/admin/reports/hackathons/{id}/export-csv`: Download CSV of team participation.
* `GET /api/admin/reports/hackathons/summary-csv`: Download CSV summary of all events.
