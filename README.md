# bracov

Tells you coverage percentage only of current branch.

Works out the lines changes in the current branch by diffing against master, then extract those lines from the jacoco report and calculate coverage statistics of only that branch.

- Only for java maven projects
- Requires jacoco set up in target project and building report into target/site/jacoco
- Requires "git" on $PATH
- Takes one argument - the location of the project root
- Writes the coverage percentage to STDOUT


## Example usage

    $ java -jar bracov-0.1.0-standalone.jar ~/workspace/myproject
