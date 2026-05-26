-- Adds job + build number fields from Jenkins build log path.
function add_build_fields(tag, timestamp, record)
    local path = record["jenkins_build_log"]
    if path == nil then
        return 0, timestamp, record
    end
    local job, build = string.match(path, "/jobs/([^/]+)/builds/(%d+)/")
    if job ~= nil then
        record["jenkins_job"] = job
    end
    if build ~= nil then
        record["build"] = build
    end
    return 2, timestamp, record
end
