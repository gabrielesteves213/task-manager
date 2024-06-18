import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TaskManager extends JFrame {
    private static final SystemInfo systemInfo = new SystemInfo();
    private static final OperatingSystem os = systemInfo.getOperatingSystem();
    private DefaultTableModel model;
    private Map<Integer, OSProcess> initialProcesses = new HashMap<>();

    public TaskManager() {
        setTitle("Task Manager");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        model = new DefaultTableModel(new Object[]{"PID", "Name", "CPU (%)", "Memory (MB)", "CPU Diff (%)", "Memory Diff (MB)", "Foreground"}, 0);
        JTable table = new JTable(model);
        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel();
        JButton listButton = new JButton("List Processes");
        JButton cpuMemButton = new JButton("Display CPU and Memory Usage");
        JButton terminateButton = new JButton("Terminate Process");

        controlPanel.add(listButton);
        controlPanel.add(cpuMemButton);
        controlPanel.add(terminateButton);
        add(controlPanel, BorderLayout.SOUTH);

        listButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                listProcesses();
            }
        });

        cpuMemButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                displayCpuMemoryUsage();
            }
        });

        terminateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String pidString = JOptionPane.showInputDialog("Enter PID to terminate:");
                if (pidString != null && !pidString.isEmpty()) {
                    try {
                        int pid = Integer.parseInt(pidString);
                        terminateProcess(pid);
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(null, "Invalid PID", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });
    }

    private void listProcesses() {
        model.setRowCount(0);
        int foregroundPid = getForegroundProcessId();
        List<OSProcess> processes = os.getProcesses();
        for (OSProcess process : processes) {
            model.addRow(new Object[]{
                    process.getProcessID(),
                    process.getName(),
                    String.format("%.2f", 100d * process.getProcessCpuLoadCumulative()),
                    String.format("%.2f", process.getResidentSetSize() / (1024.0 * 1024.0)),
                    "-", "-",  // Placeholder for CPU and Memory difference
                    process.getProcessID() == foregroundPid ? "Yes" : "No"
            });
        }
    }

    private void displayCpuMemoryUsage() {
        // Capture initial process state
        initialProcesses.clear();
        List<OSProcess> processes = os.getProcesses();
        for (OSProcess process : processes) {
            initialProcesses.put(process.getProcessID(), process);
        }

        // Delay to capture second process state
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Capture second process state and calculate differences
        Map<Integer, OSProcess> secondProcesses = new HashMap<>();
        processes = os.getProcesses();
        for (OSProcess process : processes) {
            secondProcesses.put(process.getProcessID(), process);
        }

        int foregroundPid = getForegroundProcessId();
        model.setRowCount(0);
        for (OSProcess process : processes) {
            int pid = process.getProcessID();
            OSProcess initialProcess = initialProcesses.get(pid);
            double initialCpuLoad = initialProcess != null ? initialProcess.getProcessCpuLoadCumulative() : 0;
            double secondCpuLoad = process.getProcessCpuLoadCumulative();
            double cpuDiff = 100d * (secondCpuLoad - initialCpuLoad);

            long initialMemory = initialProcess != null ? initialProcess.getResidentSetSize() : 0;
            long secondMemory = process.getResidentSetSize();
            double memoryDiff = (secondMemory - initialMemory) / (1024.0 * 1024.0);

            model.addRow(new Object[]{
                    pid,
                    process.getName(),
                    String.format("%.2f", 100d * secondCpuLoad),
                    String.format("%.2f", secondMemory / (1024.0 * 1024.0)),
                    String.format("%.2f", cpuDiff),
                    String.format("%.2f", memoryDiff),
                    pid == foregroundPid ? "Yes" : "No"
            });
        }
    }

    private int getForegroundProcessId() {
        WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null) {
            return -1;
        }
        IntByReference pid = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
        return pid.getValue();
    }

    private void terminateProcess(int pid) {
        String osName = System.getProperty("os.name").toLowerCase();
        String command = "";

        if (osName.contains("win")) {
            command = "taskkill /PID " + pid;
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("mac")) {
            command = "kill -9 " + pid;
        }

        try {
            Process process = Runtime.getRuntime().exec(command);
            process.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            if (process.exitValue() == 0) {
                JOptionPane.showMessageDialog(null, "Process terminated successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                listProcesses(); // Refresh the process list
            } else {
                JOptionPane.showMessageDialog(null, "Failed to terminate process: " + output, "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new TaskManager().setVisible(true);
            }
        });
    }

    public interface Psapi extends com.sun.jna.platform.win32.Psapi {
        Psapi INSTANCE = Native.load("psapi", Psapi.class);
        int GetProcessImageFileNameA(WinNT.HANDLE Process, byte[] lpImageFileName, int nSize);
    }

    public interface Kernel32 extends com.sun.jna.platform.win32.Kernel32 {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);
        WinNT.HANDLE OpenProcess(int dwDesiredAccess, boolean bInheritHandle, int dwProcessId);
    }

    public interface User32 extends com.sun.jna.platform.win32.User32 {
        User32 INSTANCE = Native.load("user32", User32.class);
        int GetWindowThreadProcessId(WinDef.HWND hWnd, IntByReference lpdwProcessId);
        WinDef.HWND GetForegroundWindow();
    }
}
