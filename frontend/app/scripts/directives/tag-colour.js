(function () {
    'use strict';
    angular.module('theHiveDirectives').directive('tagColour', function ($timeout, TaxonomyCacheSrv, TagSrv) {
        return {
            restrict: 'A',
            scope: {
                tag: '='
            },
            link: function (scope, element/*, attrs*/) {
                if (!scope.tag) {
                    return;
                }

                scope.bgColour = TaxonomyCacheSrv.getColour(scope.tag) ||
                    TaxonomyCacheSrv.getColour('_freetags_:' + scope.tag) ||
                    TagSrv.tagsDefaultColour ||
                    '#000000';

                $timeout(function () {
                    angular.element(element[0]).attr('style', 'background-color:' + scope.bgColour);
                });
            }
        };
    });
})();
