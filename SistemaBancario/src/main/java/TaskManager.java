import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.general.DefaultPieDataset;

import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TaskManager extends JFrame {
	
    private static final SystemInfo systemInfo = new SystemInfo();
    private static final OperatingSystem os = systemInfo.getOperatingSystem();
    
    private JLabel filtroLabel;
    private JTextField filtroTextField;
    private DefaultTableModel model;
    private Map<Integer, OSProcess> initialProcesses = new HashMap<>();

    public TaskManager() {
        setTitle("Gerenciador de Tarefas");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT)); // Usando FlowLayout para o painel superior
        filtroLabel = new JLabel("PID/Nome");
        filtroTextField = new JTextField(50);
        topPanel.add(filtroLabel); // Adicionando o campo de texto ao painel superior
        topPanel.add(filtroTextField); // Adicionando o campo de texto ao painel superior
        add(topPanel, BorderLayout.NORTH); // Adicionando o painel superior ao BorderLayout.NORTH

        model = new DefaultTableModel(new Object[]{"PID", "Nome", "Caminho", "CPU (%)", "Memoria (MB)", "CPU Diff (%)", "Memoria Diff (MB)", "Primeiro Plano"}, 0);
        JTable table = new JTable(model);
        JScrollPane scrollPane = new JScrollPane(table);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        add(scrollPane, BorderLayout.CENTER);
        
        JPanel controlPanel = new JPanel();
        JButton listButton = new JButton("Atualizar");
        JButton cpuMemButton = new JButton("Mostrar uso de CPU e Memoria");
        JButton terminateButton = new JButton("Encerrar Processo");
        JButton cpuChartButton = new JButton("Exibir gráfico de uso da CPU");
        
        controlPanel.add(listButton);
        controlPanel.add(cpuMemButton);
        controlPanel.add(terminateButton);
        controlPanel.add(cpuChartButton);
        add(controlPanel, BorderLayout.SOUTH);
        
        listButton.addActionListener(e -> listProcesses());
        
        cpuChartButton.addActionListener(e -> displayCpuUsageChart());
        
        cpuMemButton.addActionListener(e -> displayCpuMemoryUsage());
        
        terminateButton.addActionListener(e -> terminateProcess());
        
        filtroTextField.getDocument().addDocumentListener(new DocumentListener() {
            
        	public void insertUpdate(DocumentEvent e) {
                filterTable();
            }
           
            public void removeUpdate(DocumentEvent e) {
                filterTable();
            }
            
            public void changedUpdate(DocumentEvent e) {
                filterTable();
            }
            
            private void filterTable() {
            	String searchText = filtroTextField.getText().toLowerCase().trim();
                if (searchText.length() == 0) {
                    sorter.setRowFilter(null); // Mostrar todas as linhas se o campo de busca estiver vazio
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchText, 0, 1)); // Filtrar pela coluna "Name"
                }
            }
        });
    }
    
    private void terminateProcess() {
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

    private void displayCpuUsageChart() {
    	DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
    	List<OSProcess> processes = os.getProcesses();

        // Ordena os processos pelo uso de CPU (maior para menor)
        processes.sort((p1, p2) -> Double.compare(p2.getProcessCpuLoadCumulative(), p1.getProcessCpuLoadCumulative()));

        // Adiciona os processos ao dataset para o gráfico de pizza
        for (OSProcess process : processes) {
            dataset.setValue(process.getName(), process.getProcessCpuLoadCumulative() * 100);
        }

        // Cria o gráfico de pizza
        JFreeChart chart = ChartFactory.createPieChart(
                "Uso da CPU",
                dataset,
                true,
                true,
                false
        );

        // Cria um painel para exibir o gráfico
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(600, 400));

        // Exibe o gráfico em uma modal
        JFrame chartFrame = new JFrame("CPU");
        chartFrame.getContentPane().add(chartPanel, BorderLayout.CENTER);
        chartFrame.setSize(800, 600);
        chartFrame.setLocationRelativeTo(this); // Centraliza a modal em relação à janela principal
        chartFrame.setVisible(true);
    }
    
    private void listProcesses() {
        model.setRowCount(0);
        int foregroundPid = getForegroundProcessId();
        List<OSProcess> processes = os.getProcesses();
        for (OSProcess process : processes) {
        	String processPath = getProcessPath(process.getProcessID());
            model.addRow(new Object[]{
                    process.getProcessID(),
                    process.getName(),
                    processPath != null ? processPath : "N/A",
                    String.format("%.2f", 100d * process.getProcessCpuLoadCumulative()),
                    String.format("%.2f", process.getResidentSetSize() / (1024.0 * 1024.0)),
                    "-", "-",  // Espaço reservado para diferença de CPU e memória
                    process.getProcessID() == foregroundPid ? "Sim" : "Não"
            });
        }
    }

    private String getProcessPath(int pid) {
        WinNT.HANDLE processHandle = Kernel32.INSTANCE.OpenProcess(WinNT.PROCESS_QUERY_INFORMATION | WinNT.PROCESS_VM_READ, false, pid);
        if (processHandle == null) {
            return null;
        }

        byte[] pathBuffer = new byte[1024];
        int result = Psapi.INSTANCE.GetProcessImageFileNameA(processHandle, pathBuffer, pathBuffer.length);
        Kernel32.INSTANCE.CloseHandle(processHandle);

        if (result > 0) {
            String devicePath = Native.toString(pathBuffer);
			return devicePath;
        } else {
            return null;
        }
    }

    private void displayCpuMemoryUsage() {
        // Capturar o estado inicial do processo
        initialProcesses.clear();
        List<OSProcess> processes = os.getProcesses();
        for (OSProcess process : processes) {
            initialProcesses.put(process.getProcessID(), process);
        }

        // Atraso para capturar o segundo estado do processo
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Captura o segundo estado do processo e calcula as diferenças
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

            String processPath = getProcessPath(process.getProcessID());
            
            model.addRow(new Object[]{
                    pid,
                    process.getName(),
                    processPath != null ? processPath : "N/A",
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

    @SuppressWarnings("deprecation")
	private void terminateProcess(int pid) {
        String osName = System.getProperty("os.name").toLowerCase();
        String command = "";

        if (osName.contains("win")) {
            command = "taskkill /F /PID " + pid; // Adicionando /F para forçar a terminação
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("mac")) {
            command = "kill -9 " + pid;
        }

        try {
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor(); // Aguarda a execução do processo terminar

            if (exitCode == 0) {
                JOptionPane.showMessageDialog(null, "Process terminated successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                listProcesses(); // Atualiza a lista de processos após a terminação
            } else {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder errorMsg = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    errorMsg.append(line).append("\n");
                }
                JOptionPane.showMessageDialog(null, "Failed to terminate process: " + errorMsg.toString(), "Error", JOptionPane.ERROR_MESSAGE);
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
