(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('SearchSrv', function($http, NotificationSrv) {
            return function(cb, filter, objectType, range, sort, nparent, nstats) {
                var url;
                if (!angular.isString(objectType) || objectType === 'any') {
                    url = './api/_search';
                } else {
                    var entity = objectType.replace(/_/g, '/');
                    if(entity[0] === '/') {
                        entity = entity.substr(1);
                    }

                    url = './api/' + entity + '/_search';
                }

                var params = '';
                if (angular.isString(range)) {
                    params = params + '&range=' + encodeURIComponent(range);
                }
                if (angular.isString(sort)) {
                    params = params + '&sort=' + encodeURIComponent(sort);
                } else if (angular.isArray(sort)) {
                    var sortFields = _.map(sort, function(s) {
                        return 'sort=' + encodeURIComponent(s);
                    });

                    params = params + '&' + sortFields.join('&');
                }
                if (angular.isNumber(nparent)) {
                    params = params + '&nparent=' + nparent;
                }
                if (nstats === true) {
                    params = params + '&nstats=' + nstats;
                }
                $http.post(url + '?' + params.substr(1), {
                    'query': filter
                }).success(function(data, status, headers) {
                    cb(data, parseInt(headers('X-Total')));
                }).error(function(data, status) {
                    NotificationSrv.error('SearchSrv', data.type || data.message, status);
                });
            };
        });

})();
