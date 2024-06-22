
public class ProcessInfo {

	private int processId;
    private String name;
    private String path;
    private String user;
    private int threadCount;
    private long virtualSize;
    private long residentSetSize;
    private long userTime;
    private long kernelTime;
    private double cpuUsage;
    private long memoryUsage;

    public ProcessInfo(int processId) {
    	this.processId = processId;
	}

	public ProcessInfo(int processId, String name, String path, String user, int threadCount, long virtualSize, long residentSetSize, long userTime, long kernelTime, double cpuUsage, long memoryUsage) {
        this.processId = processId;
        this.name = name;
        this.path = path;
        this.user = user;
        this.threadCount = threadCount;
        this.virtualSize = virtualSize;
        this.residentSetSize = residentSetSize;
        this.userTime = userTime;
        this.kernelTime = kernelTime;
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
    }

	public int getProcessId() {
		return processId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public int getThreadCount() {
		return threadCount;
	}

	public void setThreadCount(int threadCount) {
		this.threadCount = threadCount;
	}

	public long getVirtualSize() {
		return virtualSize;
	}

	public void setVirtualSize(long virtualSize) {
		this.virtualSize = virtualSize;
	}

	public long getResidentSetSize() {
		return residentSetSize;
	}

	public void setResidentSetSize(long residentSetSize) {
		this.residentSetSize = residentSetSize;
	}

	public long getUserTime() {
		return userTime;
	}

	public void setUserTime(long userTime) {
		this.userTime = userTime;
	}

	public long getKernelTime() {
		return kernelTime;
	}

	public void setKernelTime(long kernelTime) {
		this.kernelTime = kernelTime;
	}

	public double getCpuUsage() {
		return cpuUsage;
	}

	public void setCpuUsage(double cpuUsage) {
		this.cpuUsage = cpuUsage;
	}

	public long getMemoryUsage() {
		return memoryUsage;
	}

	public void setMemoryUsage(long memoryUsage) {
		this.memoryUsage = memoryUsage;
	}

}
