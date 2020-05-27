package jenkinsci.plugins.influxdb.generators;

import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.influxdb.dto.Point;

import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.scm.ChangeLogSet;
import jenkinsci.plugins.influxdb.renderer.MeasurementRenderer;

public class ChangeLogPointGenerator extends AbstractPointGenerator {
    
	private static final String BUILD_DISPLAY_NAME = "display_name";
	private static final Logger logger = Logger.getLogger(ChangeLogPointGenerator.class.getName());

	private final Run<?, ?> build;
	private final String customPrefix;
	
	private StringBuilder affectedPaths;

	private StringBuilder messages;

	private StringBuilder culprits;

	private int commitCount = 0;

	public ChangeLogPointGenerator(MeasurementRenderer<Run<?, ?>> projectNameRenderer, String customPrefix,
			Run<?, ?> build, long timestamp, boolean replaceDashWithUnderscore) {
		super(projectNameRenderer, timestamp, replaceDashWithUnderscore);
		this.build = build;
		this.customPrefix = customPrefix;
	}

	public boolean hasReport() {
		if(build.getClass().getName().equals("org.jenkinsci.plugins.workflow.job.WorkflowRun")) {
			org.jenkinsci.plugins.workflow.job.WorkflowRun workflowRun = (org.jenkinsci.plugins.workflow.job.WorkflowRun) build;
			this.affectedPaths = new StringBuilder();

			this.messages = new StringBuilder();

			this.culprits = new StringBuilder();
			List<ChangeLogSet<? extends ChangeLogSet.Entry>> changesets = workflowRun.getChangeSets();
			for (ChangeLogSet<? extends ChangeLogSet.Entry> changeset : changesets ) {
				for (ChangeLogSet.Entry str : changeset) {
					Collection<? extends ChangeLogSet.AffectedFile> affectedFiles = str.getAffectedFiles();
					for (ChangeLogSet.AffectedFile affectedFile : affectedFiles) {
						this.affectedPaths.append(affectedFile.getPath());
						this.affectedPaths.append(", ");
					}
					this.messages.append(str.getMsg());
					this.messages.append(", ");

					this.culprits.append(str.getAuthor().getFullName());
					this.culprits.append(", ");

					this.commitCount += 1;
				}
			}
			logger.log(Level.INFO, "hasReport Commit Count:: "+ this.getCommitCount());
            return (this.getCommitCount() > 0);
        }
		if (build instanceof AbstractBuild) {
			getChangeLog(build);
			return (this.getCommitCount() > 0);
		}
		return false;
	}

	public Point[] generate() {
		Point.Builder point = buildPoint(measurementName("changelog_data"), customPrefix, build);

		point.addField(BUILD_DISPLAY_NAME, build.getDisplayName())
				.addField("commit_messages", this.getMessages())
				.addField("culprits", this.getCulprits())
				.addField("affected_paths", this.getAffectedPaths())
				.addField("commit_count", this.getCommitCount());

		return new Point[] { point.build() };
	}

	private void getChangeLog(Run<?, ?> run) {
		this.affectedPaths = new StringBuilder();

		this.messages = new StringBuilder();

		this.culprits = new StringBuilder();

		AbstractBuild<?,?> abstractBuild = (AbstractBuild<?,?>) run;
		ChangeLogSet<? extends ChangeLogSet.Entry> changeset = abstractBuild.getChangeSet();
		for (ChangeLogSet.Entry str : changeset) {
			Collection<? extends ChangeLogSet.AffectedFile> affectedFiles = str.getAffectedFiles();
			for (ChangeLogSet.AffectedFile affectedFile : affectedFiles) {
				this.affectedPaths.append(affectedFile.getPath());
				this.affectedPaths.append(", ");
			}
			this.messages.append(str.getMsg());
			this.messages.append(", ");
			
			this.culprits.append(str.getAuthor().getFullName());
			this.culprits.append(", ");
			
			this.commitCount += 1;
		}
	}

	private String getMessages() {
	    return this.messages.length() > 0 ? this.messages.substring(0, this.messages.length() - 2) : "";
	}

	private String getCulprits() {
		return this.culprits.length() > 0 ? this.culprits.substring(0, this.culprits.length() - 2) : "";
	}
	
	private String getAffectedPaths() {
		return this.affectedPaths.length() > 0 ? this.affectedPaths.substring(0, this.affectedPaths.length() - 2) : "";
	}

	private int getCommitCount() {
        return this.commitCount;
	}
}
