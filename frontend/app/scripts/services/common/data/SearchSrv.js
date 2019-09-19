(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('SearchSrv', function($http, NotificationSrv) {
            return function(cb, filter, objectType, range, sort, nparent, nstats) {
                var url;

                // Compute API url
                if (!angular.isString(objectType) || objectType === 'any') {
                    url = './api/_search';
                } else {
                    var entity = objectType.replace(/_/g, '/');
                    if(entity[0] === '/') {
                        entity = entity.substr(1);
                    }

                    url = './api/' + entity + '/_search';
                }

                // Compute API url's query params
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

                // Call the API url
                $http.post(url + '?' + params.substr(1), {
                    'query': filter
                }).then(function(response) {
                    cb(response.data, parseInt(response.headers('X-Total')));
                }).catch(function(err) {
                    var message = err.data.type || err.data.message;
                    NotificationSrv.error('SearchSrv', message, err.status);
                });
            };
        });

})();
