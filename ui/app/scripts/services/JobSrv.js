(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('JobSrv', function($resource) {
            return $resource('./api/case/artifact/:artifactId/job/:analyzerId', {}, {}, {});
        });
})();
