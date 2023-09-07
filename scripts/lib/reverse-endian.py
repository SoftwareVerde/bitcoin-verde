import sys

string = ""
for argv in sys.argv[1:]:
    string = string + argv.replace(" ", "")
for i in range(0, len(string) // 2):
    sys.stdout.write(string[len(string) - (i*2) - 2] + string[len(string) - (i*2) - 1])
sys.stdout.write('\n')
sys.stdout.flush()

