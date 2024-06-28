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
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TaskManager extends JFrame {

    private JLabel filtroLabel;
    private JTextField filtroTextField;
    private DefaultTableModel model;
    private JTable table;

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

        model = new DefaultTableModel(new Object[]{"PID", "Nome", "Caminho", "CPU (%)", "Memoria (MB)"}, 0);
        table = new JTable(model);
        JScrollPane scrollPane = new JScrollPane(table);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        add(scrollPane, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel();
        
        JButton listButton = new JButton("Atualizar");
        listButton.setBackground(Color.GREEN);
        listButton.setForeground(Color.WHITE);
        
        JButton cpuChartButton = new JButton("Exibir gráfico de uso da CPU");
        cpuChartButton.setBackground(Color.GREEN);
        cpuChartButton.setForeground(Color.WHITE);
        
        JButton terminateButton = new JButton("Encerrar Processo");
        terminateButton.setBackground(Color.RED);
        terminateButton.setForeground(Color.WHITE);
        
        controlPanel.add(listButton);
        controlPanel.add(cpuChartButton);
        controlPanel.add(terminateButton);
        add(controlPanel, BorderLayout.SOUTH);

        //Lista os processos ao iniciar
        listProcesses();

        listButton.addActionListener(e -> listProcesses());

        cpuChartButton.addActionListener(e -> displayCpuUsageChart());

        terminateButton.addActionListener(e -> terminateProcessListener());

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

    private void terminateProcessListener() {
        var selectedRow = table.getSelectedRows();
        var terminates = terminateProcessListener(selectedRow);
        
        if (terminates.contains(0)) {
            JOptionPane.showMessageDialog(null, "Process terminated successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
            listProcesses(); // Atualiza a lista de processos após a terminação
        }
	}

	private List<Integer> terminateProcessListener(int[] selectedRow) {
		var terminates = new ArrayList<Integer>();
		
		if (Objects.nonNull(selectedRow) && selectedRow.length > 0) {
            for (var row : selectedRow) {
                if (row != -1) {
                    var modelRow = table.convertRowIndexToModel(row);
                    var pid =  model.getValueAt(modelRow, 0).toString();
                    terminateProcessByPid(pid, terminates::add);
                }
            }
        } else {
            String pidString = JOptionPane.showInputDialog("Enter PID to terminate:");
            terminateProcessByPid(pidString, terminates::add);
        }
		
		return terminates;
	}

    private void terminateProcessByPid(String pidString, Consumer<Integer> consumeExitCode) {
        if (pidString != null && !pidString.isEmpty()) {
            try {
                int pid = Integer.parseInt(pidString);
                int exitCode = terminateProcess(pid);
                consumeExitCode.accept(exitCode);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(null, "PID Invalido", "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void displayCpuUsageChart() {
    	DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
    	List<ProcessInfo> processes = getProcesses();
    	 
        // Ordena os processos pelo uso de CPU (maior para menor)
        processes.sort((p1, p2) -> Double.compare(p2.getCpuUsage(), p1.getCpuUsage()));

        // Adiciona os processos ao dataset para o gráfico de pizza
        for (ProcessInfo process : processes) {
            dataset.setValue(process.getName(), process.getCpuUsage());
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
        
        List<ProcessInfo> processes = getProcesses();
        for (ProcessInfo process : processes) {
            model.addRow(new Object[]{
                    process.getProcessId(),
                    process.getName(),
                    process.getPath() != null ? process.getPath() : "N/A",
                    String.format("%.2f", process.getCpuUsage()),
                    String.format("%.2f", process.getResidentSetSize() / (1024d * 1024d))
            });
        }
    }

    @SuppressWarnings("deprecation")
	private int terminateProcess(int pid) {
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

            if (exitCode != 0) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder errorMsg = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    errorMsg.append(line).append("\n");
                }
                JOptionPane.showMessageDialog(null, "Falha ao finalizar processo: " + errorMsg.toString(), "Error", JOptionPane.ERROR_MESSAGE);
            }
            return exitCode;
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return 1; 	
        }
    }
    
    public static List<ProcessInfo> getProcesses() {
        List<ProcessInfo> processList = new ArrayList<>();

        try {
            // Comando PowerShell para listar processos com detalhes
            String command = "powershell.exe Get-Process | Select-Object Id, Name, Path, @{Name='User';Expression={(Get-Process -Id $_.Id).StartInfo.UserName}},"
            		+ "@{Name='CPU';Expression={(Get-Process -Id $_.Id).CPU}}, VirtualMemorySize, WorkingSet";

            // Executar o comando PowerShell
            Process powerShellProcess = Runtime.getRuntime().exec(command);

            // Capturar a saída do comando
            BufferedReader reader = new BufferedReader(new InputStreamReader(powerShellProcess.getInputStream()));
            String line;

            ProcessInfo process = null;
            
            // Expressão regular para capturar chave e valor
            Pattern pattern = Pattern.compile("^\\s*(?<key>[^:]+)\\s*:\\s*(?<value>.*)$");

            Double totalCpu = 0.00;
            
            // Ler cada linha de saída do PowerShell
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty()) {
                    continue; // Ignorar linhas vazias
                }

                Matcher matcher = pattern.matcher(line);
                if (!matcher.matches()) {
                	continue;
                }
                
                String key = matcher.group("key").trim();
                String value = matcher.group("value").trim();
                
                switch (key) {
                    case "Id":
                        // Se trocou de processo, adiciona na lista
                        if (process != null) {
                            processList.add(process);
                        }
                        int pid = Integer.parseInt(value);
                        process = new ProcessInfo(pid);
                        break;
                    case "Name":
                    	process.setName(value);
                        break;
                    case "Path":
                    	process.setPath(value);
                        break;
                    case "User":
                    	process.setUser(value);
                        break;
                        
                    case "CPU":
                    	if (!value.isBlank()) {
                    		Double cpuUsage = Double.parseDouble(value.trim().replace(',', '.'));
                    		process.setCpuUsage(cpuUsage);
                    		totalCpu += cpuUsage;
                    	}
                    	break;
                    case "VirtualMemorySize":
                    	process.setVirtualSize(Long.parseLong(value));
                        break;
                    case "WorkingSet":
                    	process.setResidentSetSize(Long.parseLong(value));
                        break;
                    default:
                        break;
                }
            }
            
            if (processList != null) {
            	processList.add(process);
            }

            processaPorcentagemCPU(processList, totalCpu);
            
            // Aguardar o término do processo PowerShell
            powerShellProcess.waitFor();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        return processList;
    }

	private static void processaPorcentagemCPU(List<ProcessInfo> processList, Double totalCpu) {
		processList.forEach(p -> {
			Double cpu = (100d * p.getCpuUsage()) / totalCpu;
			p.setCpuUsage(cpu);
		});
	}
    
}
