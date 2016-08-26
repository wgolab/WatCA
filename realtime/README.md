# Real-time consistency analysis using WatCA

**Instructions for EC2 and Azure, Ubuntu 14.04 server, Cassandra 2.2.7, YCSB 0.10.0**

Step 0: SSH agent

- Launch an ssh agent for passwordless authentication with VMs.

Step 1: Launch instances

- Launch Ubuntu 14.04 (64-bit) instances in one or more geographical regions.
- Use the default security group in EC2.

Step 2: Prepare security group

- Manually edit the default security group, adding an inbound rule that allows unrestricted traffic from the same group.
- Remove all other inbound rules that may have been added by the scripts in prior runs.

Step 3: Obtain list of host IPs

- Place the public IPs in `servers_public`, one per line.
- Place public/private IP pairs in `servers_public_private`, one pair per line, elements of a pair separated by a space.
- Use provided scripts (e.g., configure_ips_azure.sh) 

Step 4: Storage system setup

- Run the `setup_ubuntu_cluster.sh` script.
- Alternatively, at each host clone the git repository and then execute `setup_ubuntu.sh` to download and install Cassandra.
- Most of the installation is non-interactive, but be prepared to accept the Oracle Java license agreement at each host.
- Double-check that Cassandra got installed.  Update the `CVER` variable in `setup_ubuntu.sh` if you get an HTTP 404 response when downloading Cassandra.

Step 5: Configure tool

- Edit `settings.sh`.
- Override the first two settings, try to use defaults for the others.

Step 6: Launch tool

- Run `run_watca.sh`, and wait a few seconds for the tool to initialize before opening a browser to connect to the web interface.
- By default, the web interface binds to port 12346.
