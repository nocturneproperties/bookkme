# Set business link

`PUT /api/business/{id}/link` → `SetBusinessLink`

The link is opened as `bookkme.com/{name}`; the site resolves the name with `GET /api/business/link/{name}` (`ResolveBusinessLink`, no sign-in), which also answers for a name the business replaced within the last 90 days, returning the current name; nginx limits it to 30 lookups a minute per address (`business_link_resolve`) (the site's origin may call it, like every API route, through nginx's CORS headers).

```mermaid
flowchart TD
    Start([PUT /api/business/id/link]) --> Auth{JWT valid?}
    Auth -- No --> R401([401 Unauthorized])
    Auth -- Yes --> Normalize[BusinessLinkPolicy.normalize - trim and lowercase]
    Normalize --> Valid{3 to 32 of a-z 0-9, single hyphens between?}
    Valid -- No --> R422i([422 BUSINESS_LINK_INVALID 200049])
    Valid -- Yes --> Reserved{BusinessLinkPolicy.isReserved?}
    Reserved -- Yes --> R422r([422 BUSINESS_LINK_RESERVED 200050])
    Reserved -- No --> Tx[[Begin transaction]]
    Tx --> Lock{BusinessDataSource.lockBusiness id EXCLUSIVE - row exists?}
    Lock -- No --> R404n([404 Error.NotFound])
    Lock -- Yes --> ByName[BusinessLinkDataSource.getLinkByNameForUpdate name - locks the row or its gap]
    ByName --> Owner{BusinessDataSource.isOwner userId id?}
    Owner -- No --> R404o([404 Error.OperationNotAllowed])
    Owner -- Yes --> Existing{row for the name?}
    Existing -- none --> Claim
    Existing -- released more than 90 days ago --> Expired[deleteLink name]
    Expired --> Claim
    Existing -- another business, current or held --> R422t([422 BUSINESS_LINK_TAKEN 200051])
    Existing -- this business, current --> R200([200 BusinessLink])
    Existing -- this business, held --> Restore
    Claim[claim] --> Release
    Restore[restore] --> Release
    Release{getCurrentLink id?}
    Release -- none --> Write
    Release -- exists --> Count{countLinkChangesSince id now - 90 days at least 3?}
    Count -- Yes --> R422c([422 BUSINESS_LINK_CHANGE_LIMIT_REACHED 200052])
    Count -- No --> Released[releaseCurrentLink id now, recordLinkChange id now]
    Released --> Write
    Write[claim - createLink id name, restore - restoreLink id name] --> Commit{commit - unique index business_link_name_unique holds?}
    Commit -- No, another business claimed it concurrently --> R422t
    Commit -- Yes --> R200
```

A name released more than 90 days ago counts as free here and in the lookup even before the daily `business-deleteReleasedBusinessLinks` job deletes it and the change records older than 90 days ([Scheduled jobs](../scheduled-jobs.md)). Locking the name's row stops that job from deleting a held name while the owner switches back to it. Deleting the business deletes its current and held names (`ON DELETE CASCADE`), so they are free at once.
