package com.scc.ui;

import com.scc.dao.OrderDao;
import com.scc.dao.UserDao;
import com.scc.model.User;
import java.sql.SQLException;

/** The Admin dashboard: accounts, all orders (read only), report, export, activity log, and system records. */
public class AdminMenu {
    private final InputHelper input;
    private final UserDao userDao = new UserDao();
    private final AccountMenu accountMenu;
    private final OrderBrowser browser;
    private final ReportMenu reportMenu;
    private final LogMenu logMenu;

    public AdminMenu(InputHelper input) {
        this.input = input;
        this.accountMenu = new AccountMenu(input);
        this.browser = new OrderBrowser(input, new OrderDao());
        this.reportMenu = new ReportMenu(input);
        this.logMenu = new LogMenu(input);
    }

    public void run(User user) {
        boolean loggedIn = true;
        while (loggedIn) {
            InputHelper.header("ADMIN MENU");
            System.out.println("Welcome, " + user.fullName() + "!");
            showPendingNotice(user);
            System.out.println();
            System.out.println("1. Manage Customer Accounts");
            System.out.println("2. Manage Staff Accounts");
            System.out.println("3. Manage Admin Accounts");
            System.out.println("4. View All Laundry Orders");
            System.out.println("5. Search Laundry Orders");
            System.out.println("6. View Laundry Order Status Report");
            System.out.println("7. Export Report");
            System.out.println("8. View Laundry Activity Log");
            System.out.println("9. View System Records");
            System.out.println("10. Logout");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 10);

            try {
                switch (choice) {
                    case 1:
                        accountMenu.run(user, User.ROLE_CUSTOMER);
                        break;
                    case 2:
                        accountMenu.run(user, User.ROLE_STAFF);
                        break;
                    case 3:
                        accountMenu.run(user, User.ROLE_ADMIN);
                        break;
                    case 4:
                        browser.viewAll(user);
                        break;
                    case 5:
                        browser.search(user);
                        break;
                    case 6:
                        reportMenu.viewReport(user);
                        break;
                    case 7:
                        reportMenu.exportReport(user);
                        break;
                    case 8:
                        logMenu.run(user);
                        break;
                    case 9:
                        reportMenu.viewSystemRecords(user);
                        break;
                    default:
                        loggedIn = false;
                        break;
                }
            } catch (SQLException e) {
                InputHelper.showDbError(e);
            } catch (SecurityException e) {
                InputHelper.showAccessDenied(e);
            }
        }
    }

    // Reminds the admin when new accounts are waiting for approval.
    private void showPendingNotice(User admin) {
        try {
            int pending = userDao.countPendingAccounts(admin);
            if (pending > 0) {
                System.out.println("Notice: " + pending + " account(s) are waiting for your approval.");
            }
        } catch (SQLException e) {
            InputHelper.showDbError(e);
        }
    }
}