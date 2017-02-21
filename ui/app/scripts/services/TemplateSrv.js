(function() {
    'use strict';
    angular.module('theHiveServices')
        .factory('TemplateSrv', function($resource) {
            return $resource('./api/case/template/:templateId', {}, {
                update: {
                    method: 'PATCH',
                },
                query: {
                  method: 'POST',
                  url: './api/case/template/_search',
                  isArray: true,
                  params: {
                      range: 'all'
                  }
                }
            });
        });
})();
