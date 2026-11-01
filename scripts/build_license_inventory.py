"""Join resolved Gradle coordinates with cached POM licenses; unknowns remain explicit blockers."""
import json
import os
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

coordinates = Path(sys.argv[1])
gradle_cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))) / 'caches/modules-2/files-2.1'
rows = []
for coordinate in coordinates.read_text().splitlines():
    group, artifact, version = coordinate.split(':')
    poms = list((gradle_cache / group / artifact / version).glob('*/*.pom'))
    licenses = []
    for pom in poms:
        root = ET.parse(pom).getroot()
        namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
        for license in root.findall('m:licenses/m:license', namespace):
            licenses.append({key: license.findtext('m:' + key, default='', namespaces=namespace) for key in ('name', 'url')})
    rows.append({'coordinate': coordinate, 'declared_licenses': licenses, 'status': 'review-required' if licenses else 'missing-license-metadata'})
output = coordinates.with_name('license-inventory.json')
output.write_text(json.dumps(rows, indent=2), encoding='utf-8')
print(f'{len(rows)} components; {sum(not r["declared_licenses"] for r in rows)} without local license metadata. All require review. Output: {output}')
