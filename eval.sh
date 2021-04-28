#! /bin/bash
bash compile.sh
read -p "Press [Enter] key to continue..."
bash run.sh config
bash plot.sh
